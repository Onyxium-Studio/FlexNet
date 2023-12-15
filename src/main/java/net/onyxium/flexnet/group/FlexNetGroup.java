package net.onyxium.flexnet.group;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Builder;
import lombok.Getter;
import net.onyxium.flexnet.instance.InstanceRestarter;

import java.util.*;

@Builder
public class FlexNetGroup {

    private static final Random RANDOM = new Random();

    @Getter
    private String id;
    @Getter
    private String fromHostname;
    @Getter
    private String serverName;
    @Getter
    private String hubServer;
    @Getter
    private int maxInstance;
    @Getter
    private int playerAmountToCreateInstance;
    @Getter
    private int autoRestartInterval;
    @Getter
    private int[] restartWarningIntervals;
    @Getter
    private int postShutdownWait;

    @Builder.Default
    private transient HashMap<String, RegisteredServer> serverMap = new HashMap<>();

    public void addServer(String id, RegisteredServer server) {
        serverMap.put(id, server);
    }

    public RegisteredServer getServer(String id) {
        return serverMap.get(id);
    }

    public void removeServer(String id) {
        serverMap.remove(id);
    }

    public boolean hasServer(String id) {
        return serverMap.containsKey(id);
    }

    public Set<Map.Entry<String, RegisteredServer>> getAllServers() {
        return serverMap.entrySet();
    }

    public int getServerAmount() {
        return serverMap.size();
    }

    public RegisteredServer randomPickServer() {
        // if(serverMap.isEmpty()) throw new IllegalStateException("No server is registered in serverMap");
        RegisteredServer server = null;
        int playerCount = -1;

        // pick the server with the lowest player count
        for (Map.Entry<String, RegisteredServer> entry : serverMap.entrySet()) {
            String serverId = entry.getKey();
            RegisteredServer currentServer = entry.getValue();

            if (!InstanceRestarter.isServerRestarting(serverId)) {
                int currentServerPlayerCount = currentServer.getPlayersConnected().size();
                if (server == null || currentServerPlayerCount < playerCount) {
                    server = currentServer;
                    playerCount = currentServerPlayerCount;
                }
            }
        }

        return server;
    }

    public boolean canCreateInstance() {
        return serverMap.size() < maxInstance;
    }

    public boolean canConnect() {
        return !serverMap.isEmpty();
    }
}
