package net.onyxium.flexnet.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.onyxium.flexnet.config.FlexNetConfig;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.instance.InstanceRestarter;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JoinNewCommand implements SimpleCommand {

    // TODO: put to config
    private static final String USAGE_MESSAGE = "Usage: /JoinNew <serverId> <groupName>";
    private static final String SERVER_NOT_FOUND_MESSAGE = "Server not found: ";
    private static final String GROUP_NOT_FOUND_MESSAGE = "Group not found: ";
    private static final String SERVER_RESTARTING_MESSAGE = "Server is restarting, You can join in";

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final FlexNetGroupManager groupManager;
    private final Map<UUID, String> playerTargetServerMap;

    public JoinNewCommand(ProxyServer proxyServer, Logger logger, FlexNetConfig config,
                          FlexNetGroupManager groupManager, Map<UUID, String> playerTargetServerMap) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.groupManager = groupManager;
        this.playerTargetServerMap = playerTargetServerMap;
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.arguments().length != 2) {
            invocation.source().sendMessage(Component.text(USAGE_MESSAGE));
            return;
        }

        String targetServerId = invocation.arguments()[0];
        String groupName = invocation.arguments()[1];
        Player player = (Player) invocation.source();
        UUID playerId = player.getUniqueId();

        Optional<?> server = proxyServer.getServer(targetServerId);
        if (server.isEmpty()) {
            player.sendMessage(Component.text(SERVER_NOT_FOUND_MESSAGE + targetServerId));
            return;
        }

        if (!groupManager.hasGroup(groupName)) {
            player.sendMessage(Component.text(GROUP_NOT_FOUND_MESSAGE + groupName));
            return;
        }

        if (InstanceRestarter.isServerRestarting(targetServerId)) {
            player.sendMessage(Component.text(SERVER_RESTARTING_MESSAGE));
            return;
        }

        redirectPlayerToTargetServer(playerId, targetServerId, groupName, player);
    }

    public void redirectPlayerToTargetServer(UUID playerId, String targetServerId, String groupName, Player player) {
        playerTargetServerMap.put(playerId, targetServerId);

        String hubServerId = groupManager.getGroup(groupName).getHubServer();
        if (hubServerId != null && !hubServerId.isEmpty()) {
            proxyServer.getServer(hubServerId).ifPresent(
                    hubServer -> player.createConnectionRequest(hubServer).fireAndForget()
            );
        } else {
            logger.error("Hub server not found for group: " + groupName);
            playerTargetServerMap.remove(playerId);
        }
    }
}
