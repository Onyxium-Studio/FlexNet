package net.onyxium.flexnet.instance.pterodactyl;

import com.mattmalec.pterodactyl4j.DataType;
import com.mattmalec.pterodactyl4j.PteroBuilder;
import com.mattmalec.pterodactyl4j.application.entities.*;
import lombok.extern.slf4j.Slf4j;
import net.onyxium.flexnet.config.PterodactylConfig;
import net.onyxium.flexnet.instance.InstanceCreationResult;
import net.onyxium.flexnet.instance.InstanceManager;
import net.onyxium.flexnet.model.InstanceTemplate;
import net.onyxium.flexnet.platform.FlexNetProxy;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class PterodactylInstanceManager implements InstanceManager {

    private final PterodactylConfig config;
    private final PteroApplication api;
    private final FlexNetProxy proxy;

    public PterodactylInstanceManager(PterodactylConfig config, FlexNetProxy proxy) {
        this.config = config;
        this.proxy = proxy;
        this.api = PteroBuilder.createApplication(config.getApiUrl(), config.getApiKey());
    }

    /**
     * Create the server using Pterodactyl panel API
     * @param template Instance template
     */
    @Override
    public CompletableFuture<InstanceCreationResult> createInstance(InstanceTemplate template) {
        return CompletableFuture.supplyAsync(() -> {
            Nest nest = api.retrieveNestById(template.getNestId()).execute();
            Location loc = api.retrieveLocationById(template.getLocationId()).execute();
            ApplicationEgg egg = api.retrieveEggById(nest, template.getEggId()).execute();
            ApplicationUser owner = api.retrieveUserById(template.getDefaultOwnerId()).execute();
            Optional<ApplicationAllocation> optAllocation = api.retrieveAllocations()
                    .execute()
                    .stream()
                    .filter(appAllocation ->
                            !appAllocation.isAssigned() &&
                                    appAllocation.getAlias().startsWith(config.getAllocationAliasPrefix())
                    )
                    .findFirst();

            if(optAllocation.isEmpty()) {
                throw new IllegalStateException("No available allocation found");
            }
            log.info("Allocation {} found, creating server...", optAllocation.get().getAlias());
            log.info("Owner: {} | Location: {} | Egg: {}", owner.getFullName(), loc.getId(), egg.getName());

            try {
                ApplicationServer server = api.createServer()
                        .setName(template.getNameTemplate())
                        .setDescription(template.getDescription())
                        .setAllocations(optAllocation.get())
                        .setOwner(owner)
                        .setEgg(egg)
                        .setLocation(loc)
                        .setCPU(template.getCpuAmount())
                        .setMemory(template.getMemoryAmount(), DataType.MB)
                        .setDisk(template.getDiskAmount(), DataType.MB)
                        .startOnCompletion(true)
                        .execute();

                return InstanceCreationResult.builder()
                        .instanceId(server.getIdentifier())
                        .instanceName(server.getName())
                        .address(new InetSocketAddress(optAllocation.get().getIP(), optAllocation.get().getPortInt()))
                        .build();
            } catch (Exception e) {
                log.error("Failed to create server", e);
                throw new RuntimeException(e);
            }
        });
    }

}
