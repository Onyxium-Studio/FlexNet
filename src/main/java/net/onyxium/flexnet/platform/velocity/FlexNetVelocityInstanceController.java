package net.onyxium.flexnet.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import lombok.extern.slf4j.Slf4j;
import net.onyxium.flexnet.config.FlexNetConfig;
import net.onyxium.flexnet.group.FlexNetGroup;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.instance.InstanceCreationResult;
import net.onyxium.flexnet.instance.InstanceManager;
import net.onyxium.flexnet.model.InstanceTemplate;
import net.onyxium.flexnet.platform.FlexNetProxy;
import net.onyxium.flexnet.platform.velocity.event.FlexNetVelocityPlayerForwardedEvent;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class FlexNetVelocityInstanceController {

    private final FlexNetProxy proxy;
    private final FlexNetGroupManager groupManager;
    private final InstanceManager instanceManager;
    private final FlexNetConfig config;

    public FlexNetVelocityInstanceController(FlexNetProxy proxy, FlexNetGroupManager groupManager, InstanceManager instanceManager, FlexNetConfig config) {
        this.proxy = proxy;
        this.groupManager = groupManager;
        this.instanceManager = instanceManager;
        this.config = config;
        createEmptyServerCollectorTask();
        createServerOnInit();
    }

    private void createEmptyServerCollectorTask() {
        proxy.scheduleRepeatTask(() -> {
            groupManager.getAllGroups().forEach(group -> {
                // TODO
            });
        }, 0L, 60L);
    }

    private void createServerOnInit() {
        groupManager.getAllGroups()
                .stream()
                .filter(group -> config.getTemplates().containsKey(group.getId()))
                .forEach(group -> {
                    createInstance(config.getTemplates().get(group.getId()), group);
                });
    }

    @Subscribe
    public void onFlexNetPlayerForward(FlexNetVelocityPlayerForwardedEvent event) {
        // Skip creating instance if the group is full
        if(!event.getGroup().canCreateInstance()) {
            return;
        }

        // Skip creating instance if player count is not enough
        if(event.getGroup().getPlayerAmountToCreateInstance() > event.getServer().getPlayersConnected().size()) {
            return;
        }

        // Skip creating instance if template not found
        if(config.getTemplates().containsKey(event.getGroup().getId())) {
            log.warn("Template {} not found for group {}", event.getGroup().getId(), event.getGroup().getServerName());
            return;
        }

        InstanceTemplate template = config.getTemplates().get(event.getGroup().getId());
        createInstance(template, event.getGroup());
    }

    private void createInstance(InstanceTemplate template, FlexNetGroup group) {
        log.info("Creating instance for group {}", group.getServerName());
        CompletableFuture<InstanceCreationResult> createInstanceFuture = instanceManager.createInstance(template);
        createInstanceFuture.thenAccept(result -> {
            if(result.isSuccess()) {
                proxy.scheduleTask(() -> {
                    proxy.addServer(result.getInstanceId(), result.getAddress(), group);
                    log.info("Created instance {} for group {}", result.getInstanceId(), group.getServerName());
                }, template.getServerOnlineDelay());
            } else {
                log.warn("Failed to create instance for group {}", group.getServerName());
            }
        });
    }

}
