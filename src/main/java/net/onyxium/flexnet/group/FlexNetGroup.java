package net.onyxium.flexnet.group;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Builder;
import lombok.Getter;

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
    private int maxInstance;
    @Getter
    private int playerAmountToCreateInstance;

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
        int playerCount = -1;
        RegisteredServer server = null;
        // pick the server with lowest player count
        for(RegisteredServer s : serverMap.values()) {
            if(playerCount == -1 || s.getPlayersConnected().size() <= playerCount) {
                playerCount = s.getPlayersConnected().size();
                server = s;
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
