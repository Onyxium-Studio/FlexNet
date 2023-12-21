package net.onyxium.flexnet.group;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.onyxium.flexnet.instance.InstanceLifecycleManager;

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
    private int[] transferWarningIntervals;
    @Getter
    private int postShutdownWait;

    @Builder.Default
    private transient HashMap<String, RegisteredServer> serverMap = new HashMap<>();

    // TODO: config or something
    @Setter
    @Getter
    private int validServerCount = 1;

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

    public RegisteredServer getLowestPlayerServer() {
        return serverMap.entrySet().stream()
                .filter(entry -> !InstanceLifecycleManager.isInstanceInLifecycleProcess(entry.getKey()))
                .min(Comparator.comparingInt(entry -> entry.getValue().getPlayersConnected().size()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public int getAllPlayersCount() {
        return serverMap.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(server -> server.getPlayersConnected().size())
                .sum();
    }

    public boolean canCreateInstance() {
        long activeServersCount = serverMap.entrySet().stream()
                .filter(entry -> !InstanceLifecycleManager.isInstanceInLifecycleProcess(entry.getKey()))
                .count();

        return activeServersCount < maxInstance &&
                (activeServersCount == 0 || (getAllPlayersCount() / activeServersCount) >= playerAmountToCreateInstance);
    }

    public boolean needDeleteInstance() {
        long activeServersCount = serverMap.entrySet().stream()
                .filter(entry -> !InstanceLifecycleManager.isInstanceInLifecycleProcess(entry.getKey()))
                .count();

        return activeServersCount > 1 && (getAllPlayersCount() / activeServersCount) < playerAmountToCreateInstance;
    }

    public int calculateRequiredServers() {
        return (int) Math.ceil((double) getAllPlayersCount() / playerAmountToCreateInstance);
    }

    public boolean canConnect() {
        return !serverMap.isEmpty();
    }


}
