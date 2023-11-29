package net.onyxium.flexnet.config;

import lombok.Data;
import net.onyxium.flexnet.model.InstanceTemplate;

import java.util.Map;

@Data
public class FlexNetConfig {
    private PterodactylConfig pterodactyl;
    private LocaleConfig locale;
    private Map<String, SingleGroupConfig> groups;
    private Map<String, InstanceTemplate> templates;
}
