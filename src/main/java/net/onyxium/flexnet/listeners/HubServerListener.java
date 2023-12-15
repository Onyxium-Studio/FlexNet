package net.onyxium.flexnet.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.extern.slf4j.Slf4j;
import net.onyxium.flexnet.platform.FlexNetProxy;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class HubServerListener {

    private final ProxyServer proxyServer;
    private final Map<UUID, String> playerTargetServerMap;
    private final FlexNetProxy proxy;

    public HubServerListener(FlexNetProxy proxy, ProxyServer proxyServer, Map<UUID, String> playerTargetServerMap) {
        this.proxyServer = proxyServer;
        this.playerTargetServerMap = playerTargetServerMap;
        this.proxy = proxy;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        String targetServerId = playerTargetServerMap.get(playerId);
        if (targetServerId != null) {
            proxyServer.getServer(targetServerId).ifPresent(targetServer -> {
                if (!targetServer.getServerInfo().equals(event.getServer().getServerInfo())) {
                    proxy.scheduleTask(() -> {
                        log.info("Redirecting player {} to server {}", player.getUsername(), targetServerId);
                        player.createConnectionRequest(targetServer).fireAndForget();
                    }, 1); // TODO: delay 1 is enough for all cases?
                }
                playerTargetServerMap.remove(playerId);
            });
        }
    }
}
