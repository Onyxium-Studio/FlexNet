package net.onyxium.flexnet.config;

import lombok.Data;

@Data
public class LocaleConfig {
    private String invalidHostname;
    private String failedToConnect;
    private String noServerAvailable;
    private String serverRestartWarning;
}
