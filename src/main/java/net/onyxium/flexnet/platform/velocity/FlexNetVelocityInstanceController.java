package net.onyxium.flexnet.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.onyxium.flexnet.config.FlexNetConfig;
import net.onyxium.flexnet.group.FlexNetGroup;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.instance.InstanceLifecycleManager;
import net.onyxium.flexnet.instance.InstanceManager;
import net.onyxium.flexnet.instance.InstanceRestarter;
import net.onyxium.flexnet.model.InstanceTemplate;
import net.onyxium.flexnet.platform.FlexNetProxy;
import net.onyxium.flexnet.util.TaskUtils;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
public class FlexNetVelocityInstanceController {

    private final FlexNetProxy proxy;
    private final FlexNetGroupManager groupManager;
    private final InstanceManager instanceManager;
    private final FlexNetConfig config;
    @Setter
    private InstanceLifecycleManager instanceLifecycleManager;

    private final Set<String> createdInstanceIdentifiers = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> creatingInstances = new ConcurrentHashMap<>();

    public FlexNetVelocityInstanceController(
            FlexNetProxy proxy,
            FlexNetGroupManager groupManager,
            InstanceManager instanceManager,
            FlexNetConfig config,
            InstanceLifecycleManager instanceLifecycleManager
    ) {
        this.proxy = proxy;
        this.groupManager = groupManager;
        this.instanceManager = instanceManager;
        this.config = config;
        this.instanceLifecycleManager = instanceLifecycleManager;
        createServerOnInit();
    }

    protected void onServerStop() {
        log.info("Stopping FlexNet...");
        if (FlexNetVelocityPlugin.restartTask != null) {
            FlexNetVelocityPlugin.restartTask.cancel();
        }

        CompletableFuture<Void> waitForAllInstances = CompletableFuture.allOf(
                creatingInstances.values().toArray(new CompletableFuture[0])
        );
        waitForAllInstances.join();

        createdInstanceIdentifiers.forEach(id ->
                TaskUtils.runBlocking((latch) -> {
                    instanceManager.deleteInstance(id, isSuccess -> {
                        if (isSuccess) {
                            log.info("Instance id {} successfully removed after deletion.", id);
                        } else {
                            log.warn("Failed to delete instance id {}.", id);
                        }
                        createdInstanceIdentifiers.remove(id);
                        latch.countDown();
                    });
                })
        );
    }

    public void removeInstanceId(String instanceId) {
        if (createdInstanceIdentifiers.contains(instanceId)) {
            createdInstanceIdentifiers.remove(instanceId);
            log.info("Instance id {} removed from createdInstanceIdentifiers.", instanceId);
        } else {
            log.warn("Attempted to remove non-existing instance id {} from createdInstanceIdentifiers.", instanceId);
        }
    }

    // TODO: check the is necessary?
//    private void createServerCleanupTask() {
//        proxy.scheduleRepeatTask(() -> {
//            groupManager.getAllGroups()
//                    .stream()
//                    .filter(group -> group.getServerAmount() > 1)
//                    .forEach(group -> {
//                        HashSet<String> pendingDeleteIds = new HashSet<>();
//                        group.getAllServers()
//                                .forEach(entry -> {
//                                    if(entry.getValue().getPlayersConnected().isEmpty()) {
//                                        pendingDeleteIds.add(entry.getKey());
//                                    }
//                                });
//                        pendingDeleteIds.forEach(id -> {
//                            log.info("createServerCleanupTask: Removing server {} from group {}", id, group.getServerName());
//                            proxy.removeServer(id, group);
//                            instanceManager.deleteInstance(id, (b) -> {});
//                        });
//                    });
//        }, 0L, 300L);
//    }

    private void createServerOnInit() {
        groupManager.getAllGroups()
                .stream()
                .filter(group -> config.getTemplates().containsKey(group.getId()))
                .forEach(group -> {
                    createInstance(config.getTemplates().get(group.getId()), group);
                });
    }

    @Subscribe
    public void onProxyStop(ProxyShutdownEvent event) {
        onServerStop();
    }

    public CompletableFuture<String> createInstance(InstanceTemplate template, FlexNetGroup group) {
        CompletableFuture<String> future = new CompletableFuture<>();
        log.info("Creating instance for group {}", group.getServerName());

        String instanceKey = group.getServerName() + "-" + System.currentTimeMillis();
        creatingInstances.put(instanceKey, future);

        instanceManager.createInstance(template, (result) -> {
            if (result.isSuccess()) {
                String instanceId = result.getInstanceId();
                proxy.scheduleTask(() -> {
                    InstanceRestarter.trackServer(instanceId);
                    proxy.addServer(instanceId, result.getAddress(), group);
                    log.info("Created instance {} for group {}", instanceId, group.getServerName());
                    createdInstanceIdentifiers.add(instanceId);
                    creatingInstances.remove(instanceKey);
                    future.complete(instanceId);
                }, template.getServerOnlineDelay());
            } else {
                // TODO: handle error
                log.warn("Failed to create instance for group {}", group.getServerName());
                creatingInstances.remove(instanceKey);
                future.completeExceptionally(new RuntimeException("Failed to create instance"));}
        });

        return future;
    }

    public void adjustInstanceCountOnPlayerJoin(FlexNetGroup group) {
        if (!group.canCreateInstance()) {
            return;
        }
        if (!config.getTemplates().containsKey(group.getId())) {
            log.warn("Template {} not found for group {}", group.getId(), group.getServerName());
            return;
        }

        int requiredServers = group.calculateRequiredServers();
        long totalInstances = group.getValidServerCount();

        for (int i = 0; i < requiredServers - totalInstances; i++) {
            log.info("requiredServers: {}, totalInstances: {}", requiredServers, totalInstances);
            log.info("Creating additional instance for group {}", group.getServerName());
            group.setValidServerCount(group.getValidServerCount() + 1);
            createInstance(config.getTemplates().get(group.getId()), group);
        }
    }

    public void adjustInstanceCountOnPlayerLeave(FlexNetGroup group) {
        if (!group.needDeleteInstance()) {
            return;
        }
        if (!config.getTemplates().containsKey(group.getId())) {
            log.warn("Template {} not found for group {}", group.getId(), group.getServerName());
            return;
        }

        RegisteredServer lowestPlayerServer = group.getLowestPlayerServer();
        if (lowestPlayerServer != null) {
            log.info("Player count is low, deleting server {} from group {}", lowestPlayerServer.getServerInfo().getName(), group.getServerName());
            String serverId = lowestPlayerServer.getServerInfo().getName();
            group.setValidServerCount(group.getValidServerCount() - 1);
            instanceLifecycleManager.handleServerLifecycle(serverId, group, false);
        }
    }
}

