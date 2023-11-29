package net.onyxium.flexnet.config;

import lombok.Data;

@Data
public class SingleGroupConfig {
    private String fromHostname;
    private String serverName;
    private int maxInstance;
    private int playerAmountToCreateInstance;
}
