package net.onyxium.flexnet.platform.velocity;

import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.onyxium.flexnet.command.JoinNewCommand;
import net.onyxium.flexnet.config.FlexNetConfig;
import net.onyxium.flexnet.group.FlexNetGroup;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.instance.InstanceManager;
import net.onyxium.flexnet.instance.InstanceRestarter;
import net.onyxium.flexnet.instance.pterodactyl.PterodactylInstanceManager;
import net.onyxium.flexnet.listeners.HubServerListener;
import net.onyxium.flexnet.platform.FlexNetProxy;
import net.onyxium.flexnet.util.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Plugin(
        id = "flexnet",
        name = "FlexNet",
        version = "1.0.0",
        description = "Velocity plugin for adding sub-servers dynamically",
        authors = { "Onyxium Studio" }
)
public class FlexNetVelocityPlugin implements FlexNetProxy {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataFolder;
    private final Supplier<FlexNetConfig> configSupplier = Suppliers.memoize(this::getConfig);

    private InstanceManager instanceManager;
    private FlexNetVelocityInstanceController instanceController;
    private FlexNetGroupManager groupManager;
    private InstanceRestarter instanceRestarter;
    private JoinNewCommand joinNewCommand;
    private final Map<UUID, String> playerTargetServerMap = new ConcurrentHashMap<>();

    @Inject
    public FlexNetVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataFolder) {
        this.proxyServer = server;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("FlexNet is initializing...");

        FlexNetConfig config = configSupplier.get(); // init config

        this.instanceManager = new PterodactylInstanceManager(config.getPterodactyl(), this);
        this.groupManager = new FlexNetGroupManager(config);
        this.instanceController = new FlexNetVelocityInstanceController(this, groupManager, instanceManager, config);
        this.joinNewCommand = new JoinNewCommand(proxyServer, logger, config, groupManager, playerTargetServerMap);
        this.instanceRestarter = new InstanceRestarter(this, groupManager, instanceManager, instanceController,
                config, joinNewCommand);
        HubServerListener hubServerListener = new HubServerListener(this, proxyServer, playerTargetServerMap);

        proxyServer.getEventManager().register(this,
                new FlexNetVelocityPlayerForwarder(proxyServer, groupManager, config, proxyServer));
        proxyServer.getEventManager().register(this, instanceController);
        proxyServer.getCommandManager().register("JoinNew", joinNewCommand);
        proxyServer.getEventManager().register(this, hubServerListener);


        // Check and restart servers every 60 seconds
        this.scheduleRepeatTask(instanceRestarter::checkAndRestartServers, 1L, 60L);
    }

    /**
     * Method to read the config file, create a new one if not exists
     * @return FlexNetConfig object converted from toml config
     */
    private FlexNetConfig getConfig() {
        File dataFolder = this.dataFolder.toFile();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        File file = new File(dataFolder, "config.toml");
        if (!file.exists())
            FileUtils.copyFileFromJar(getClass().getClassLoader(), "config.toml", file.toPath());

        return new Toml().read(file).to(FlexNetConfig.class);
    }

    @Override
    public void addServer(String identifier, InetSocketAddress address, FlexNetGroup group) {
        RegisteredServer server = proxyServer.registerServer(new ServerInfo(identifier, address));
        group.addServer(identifier, server);
    }

    @Override
    public void removeServer(String identifier, FlexNetGroup group) {
        proxyServer.getServer(identifier).ifPresent(server -> proxyServer.unregisterServer(server.getServerInfo()));
        group.removeServer(identifier);
    }

    @Override
    public void scheduleTask(Runnable runnable, long delay) {
        proxyServer.getScheduler().buildTask(this, runnable)
                .delay(Duration.of(delay, ChronoUnit.SECONDS))
                .schedule();
    }

    @Override
    public void scheduleRepeatTask(Runnable runnable, long delay, long interval) {
        proxyServer.getScheduler().buildTask(this, runnable)
                .delay(Duration.of(delay, ChronoUnit.SECONDS))
                .repeat(Duration.of(interval, ChronoUnit.SECONDS))
                .schedule();
    }

}
