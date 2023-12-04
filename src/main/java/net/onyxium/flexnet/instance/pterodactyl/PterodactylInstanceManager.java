package net.onyxium.flexnet.instance.pterodactyl;

import com.mattmalec.pterodactyl4j.DataType;
import com.mattmalec.pterodactyl4j.PowerAction;
import com.mattmalec.pterodactyl4j.PteroBuilder;
import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mattmalec.pterodactyl4j.application.entities.*;
import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.client.entities.PteroClient;
import com.mattmalec.pterodactyl4j.client.entities.Utilization;
import lombok.extern.slf4j.Slf4j;
import net.onyxium.flexnet.config.PterodactylConfig;
import net.onyxium.flexnet.instance.InstanceCreationResult;
import net.onyxium.flexnet.instance.InstanceManager;
import net.onyxium.flexnet.model.InstanceTemplate;
import net.onyxium.flexnet.platform.FlexNetProxy;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
public class PterodactylInstanceManager implements InstanceManager {

    private final PterodactylConfig config;
    private final PteroApplication api;
    private final PterodactylInstanceWatcher watcher;
    private final FlexNetProxy proxy;

    public PterodactylInstanceManager(PterodactylConfig config, FlexNetProxy proxy) {
        this.config = config;
        this.proxy = proxy;
        this.api = PteroBuilder.createApplication(config.getApiUrl(), config.getApiKey());
        PteroClient client = PteroBuilder.createClient(config.getApiUrl(), config.getClientApiKey());
        this.watcher = new PterodactylInstanceWatcher(proxy, client);
    }

    /**
     * Create the server using Pterodactyl panel API
     * @param template Instance template
     */
    @Override
    public void createInstance(InstanceTemplate template, Consumer<InstanceCreationResult> resultConsumer) {
        CompletableFuture.runAsync(() -> {
            Nest nest = api.retrieveNestById(template.getNestId()).execute();
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

            if (optAllocation.isEmpty()) {
                throw new IllegalStateException("No available allocation found");
            }
            log.info("Allocation {} found, creating server...", optAllocation.get().getAlias());
            log.info("Owner: {} | Egg: {}", owner.getFullName(), egg.getName());

            int attempt = 0;
            while (attempt < 3) {
                try {
                    ApplicationServer server = createServer(template, optAllocation, owner, egg);
                    executeWatcher(server, resultConsumer, optAllocation);
                    log.info("Server {} created successfully", server.getName());
                    return;
                } catch (Exception e) {
                    log.error("Attempt {} - Failed to create server: {}", attempt + 1, e.getMessage());
                    if (attempt == 2) {
                        log.error("All attempts to create the server have failed.");
                    }
                }
                attempt++;
            }
        }).join();
    }

    private ApplicationServer createServer(InstanceTemplate template, Optional<ApplicationAllocation> optAllocation,
                                           ApplicationUser owner, ApplicationEgg egg) throws Exception {
        if (optAllocation.isEmpty()) {
            throw new IllegalStateException("Allocation is not present");
        }

        return api.createServer()
                .setName(template.getNameTemplate())
                .setDescription(template.getDescription())
                .setAllocations(optAllocation.get())
                .setOwner(owner)
                .setEgg(egg)
                .setCPU(template.getCpuAmount())
                .setMemory(template.getMemoryAmount(), DataType.MB)
                .setDisk(template.getDiskAmount(), DataType.MB)
                .skipScripts(template.isSkipInitScript())
                .execute();
    }

    private void executeWatcher(ApplicationServer server, Consumer<InstanceCreationResult> resultConsumer,
                                Optional<ApplicationAllocation> optAllocation) {
        if (optAllocation.isEmpty()) {
            throw new IllegalStateException("Allocation is not present");
        }

        watcher.createTask(
                server.getIdentifier(),
                clientServer -> {},
                clientServer -> {
                    log.info("Server {} installing: {} suspended: {}", clientServer.getName(), clientServer.isInstalling(), clientServer.isSuspended());
                    if (clientServer.isInstalling() || clientServer.isSuspended()) return false;
                    Utilization utilization = clientServer.retrieveUtilization().execute();
                    log.info("Server {} state = {}", clientServer.getName(), utilization.getState());
                    if (utilization.getState() == UtilizationState.OFFLINE) {
                        clientServer.start().execute();
                    }
                    return utilization.getState() == UtilizationState.RUNNING;
                },
                clientServer -> resultConsumer.accept(
                        InstanceCreationResult.builder()
                                .instanceId(server.getIdentifier())
                                .instanceName(server.getName())
                                .address(new InetSocketAddress(optAllocation.get().getIP(), optAllocation.get().getPortInt()))
                                .success(true)
                                .build()
                )
        );
    }

    @Override
    public void deleteInstance(String identifier, Consumer<Boolean> callback) {
        log.info("Deleting instance {}...", identifier);
        watcher.createTask(
                identifier,
                clientServer -> clientServer.stop().execute(),
                clientServer -> {
                    Utilization utilization = clientServer.retrieveUtilization().execute();
                    log.info("Server {} state = {}", identifier, utilization.getState());
                    return utilization.getState() == UtilizationState.OFFLINE;
                },
                clientServer -> {
                    api.retrieveServerById(clientServer.getInternalIdLong())
                            .execute()
                            .getController()
                            .delete(false)
                            .execute();
                    callback.accept(true);
                }
        );
    }

}
