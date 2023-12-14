package net.onyxium.flexnet.platform.velocity;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.onyxium.flexnet.config.FlexNetConfig;
import net.onyxium.flexnet.config.LocaleConfig;
import net.onyxium.flexnet.group.FlexNetGroup;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.platform.velocity.event.FlexNetVelocityPlayerForwardedEvent;

import java.net.InetSocketAddress;

@Slf4j
public class FlexNetVelocityPlayerForwarder {

    private final ProxyServer proxyServer;
    private final LocaleConfig locale;
    private final FlexNetGroupManager groupManager;

    public FlexNetVelocityPlayerForwarder(ProxyServer server, FlexNetGroupManager groupManager, FlexNetConfig config, ProxyServer proxyServer) {
        this.proxyServer = server;
        this.groupManager = groupManager;
        this.locale = config.getLocale();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();

        if(player.getVirtualHost().isEmpty()) {
            event.setResult(ResultedEvent.ComponentResult.denied(
                    Component.text(locale.getInvalidHostname())
            ));
            log.warn(
                    "Player {} ({}) attempt to join the server without VHost",
                    player.getGameProfile().getName(),
                    player.getUniqueId()
            );
        }
        InetSocketAddress address = player.getVirtualHost().get();
        if(address.getHostName() == null || !groupManager.hasGroupFromHost(address.getHostName())) {
            // Kick player if their hostname are not listed in config
            event.setResult(ResultedEvent.ComponentResult.denied(
                    Component.text(locale.getInvalidHostname())
            ));
            log.warn(
                    "Player {} ({}) attempt to join the server with invalid VHost: {}",
                    player.getGameProfile().getName(),
                    player.getUniqueId(),
                    address.getHostName()
            );
        } else if(!groupManager.getGroupFromHost(address.getHostName()).canConnect()) {
            // Kick player if the group has no server available
            event.setResult(ResultedEvent.ComponentResult.denied(
                    Component.text(locale.getNoServerAvailable())
            ));
            log.warn(
                    "Player {} ({}) attempt to join the server with no available server in group {}",
                    player.getGameProfile().getName(),
                    player.getUniqueId(),
                    groupManager.getGroupFromHost(address.getHostName()).getId()
            );
        }
    }

    @Subscribe
    public void onChooseInitServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        if(player.getVirtualHost().isEmpty()) return;
        InetSocketAddress address = player.getVirtualHost().get();
        FlexNetGroup group = groupManager.getGroupFromHost(address.getHostName());
        RegisteredServer server = group.randomPickServer();
        event.setInitialServer(server);

        proxyServer.getEventManager().fireAndForget(new FlexNetVelocityPlayerForwardedEvent(player, group, server));

        log.info("Forwarded player {} ({}) to server {}",
                player.getGameProfile().getName(),
                player.getUniqueId(),
                server.getServerInfo().getName()
        );
    }

}
