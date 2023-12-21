package net.onyxium.flexnet.group;

import lombok.extern.slf4j.Slf4j;
import net.onyxium.flexnet.config.FlexNetConfig;

import java.util.ArrayList;
import java.util.HashMap;

@Slf4j
public class FlexNetGroupManager {

    private final FlexNetConfig config;
    private final HashMap<String, FlexNetGroup> groupIdMap = new HashMap<>();
    private final HashMap<String, FlexNetGroup> groupFromHostMap = new HashMap<>();

    public FlexNetGroupManager(FlexNetConfig config) {
        this.config = config;
        initMap();
    }

    private void initMap() {
        config.getGroups().forEach((key, value) -> {
            FlexNetGroup group = FlexNetGroup.builder()
                    .id(key)
                    .fromHostname(value.getFromHostname())
                    .serverName(value.getServerName())
                    .hubServer(value.getHubServer())
                    .autoRestartInterval(value.getAutoRestartInterval())
                    .transferWarningIntervals(value.getTransferWarningIntervals())
                    .postShutdownWait(value.getPostShutdownWait())
                    .build();
            groupIdMap.put(key, group);
            groupFromHostMap.put(group.getFromHostname(), group);
        });
        log.info("Loaded {} groups: {}", groupIdMap.size(), String.join(", ", groupIdMap.keySet()));
    }

    public FlexNetGroup getGroup(String id) {
        return groupIdMap.get(id);
    }

    public FlexNetGroup getGroupFromHost(String fromHostname) {
        return groupFromHostMap.get(fromHostname);
    }

    public boolean hasGroup(String id) {
        return groupIdMap.containsKey(id);
    }

    public boolean hasGroupFromHost(String fromHostname) {
        return groupFromHostMap.containsKey(fromHostname);
    }

    public ArrayList<FlexNetGroup> getAllGroups() {
        return new ArrayList<>(groupIdMap.values());
    }

}
