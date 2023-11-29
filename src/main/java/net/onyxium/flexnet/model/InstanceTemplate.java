package net.onyxium.flexnet.model;

import lombok.Data;

import java.util.Map;

@Data
public class InstanceTemplate {
    private long serverOnlineDelay;
    private String nameTemplate;
    private String description;
    private String dockerImage;
    private long locationId;
    private long nestId;
    private long eggId;
    private long defaultOwnerId;
    private long cpuAmount;
    private long memoryAmount;
    private long diskAmount;
    private Map<String, Object> environmentValues;
}
