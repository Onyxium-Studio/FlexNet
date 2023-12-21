package net.onyxium.flexnet.instance;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.extern.slf4j.Slf4j;
import net.onyxium.flexnet.group.FlexNetGroup;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.platform.FlexNetProxy;

import java.util.HashMap;

@Slf4j
public class InstanceRestarter {
    private final FlexNetProxy proxy;
    private final FlexNetGroupManager groupManager;
    private final InstanceLifecycleManager instanceLifecycleManager;

    private static final HashMap<String, Long> serverUptime = new HashMap<>();

    public InstanceRestarter(FlexNetProxy proxy, FlexNetGroupManager groupManager, InstanceLifecycleManager instanceLifecycleManager) {
        this.proxy = proxy;
        this.groupManager = groupManager;
        this.instanceLifecycleManager = instanceLifecycleManager;
    }

    public static void trackServer(String serverId) {
        serverUptime.put(serverId, System.currentTimeMillis());
    }

    public void checkAndRestartServers() {
        groupManager.getAllGroups().forEach(this::processGroupForRestart);
    }

    private void processGroupForRestart(FlexNetGroup group) {
        group.getAllServers().stream()
                .filter(entry -> !InstanceLifecycleManager.isInstanceInLifecycleProcess(entry.getKey()))
                .forEach(entry -> checkServerForRestart(entry.getKey(), entry.getValue(), group));
    }

    private void checkServerForRestart(String serverId, RegisteredServer server, FlexNetGroup group) {
        long uptime = getServerUptime(serverId);
        int restartInterval = group.getAutoRestartInterval();

        if (uptime >= restartInterval) {
            instanceLifecycleManager.handleServerLifecycle(serverId, group, true);
        }
    }

    private long getServerUptime(String serverId) {
        Long startTime = serverUptime.get(serverId);
        if (startTime == null) {
            log.error("Server {} not found in serverUptime", serverId);
            return 0;
        }
        long serverUptimeValue = (System.currentTimeMillis() - startTime) / (60 * 1000);
        log.info("Server {} uptime: {} minutes", serverId, serverUptimeValue);
        return serverUptimeValue;
    }

    public static void removeFromServerUptime(String serverId) {
        serverUptime.remove(serverId);
    }

}
