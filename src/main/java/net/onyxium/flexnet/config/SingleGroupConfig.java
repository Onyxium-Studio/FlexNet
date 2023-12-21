package net.onyxium.flexnet.config;

import lombok.Data;

@Data
public class SingleGroupConfig {
    private String fromHostname;
    private String serverName;
    private int maxInstance; // TODO
    private int playerAmountToCreateInstance; // TODO
    private String hubServer;
    private int autoRestartInterval;
    private int[] transferWarningIntervals;
    private int postShutdownWait;
}
