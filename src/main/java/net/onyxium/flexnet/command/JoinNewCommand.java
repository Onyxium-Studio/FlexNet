package net.onyxium.flexnet.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.onyxium.flexnet.config.FlexNetConfig;
import net.onyxium.flexnet.config.LocaleConfig;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.instance.InstanceRestarter;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JoinNewCommand implements SimpleCommand {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final FlexNetGroupManager groupManager;
    private final Map<UUID, String> playerTargetServerMap;

    private final String usageMessage;
    private final String serverNotFoundMessage;
    private final String groupNotFoundMessage;
    private final String serverRestartingMessage;

    public JoinNewCommand(ProxyServer proxyServer, Logger logger, FlexNetConfig config,
                          FlexNetGroupManager groupManager, Map<UUID, String> playerTargetServerMap) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.groupManager = groupManager;
        this.playerTargetServerMap = playerTargetServerMap;
        LocaleConfig locale = config.getLocale();

        this.usageMessage = locale.getJoinNewCommandUsage();
        this.serverNotFoundMessage = locale.getJoinNewServerNotFound();
        this.groupNotFoundMessage = locale.getJoinNewGroupNotFound();
        this.serverRestartingMessage = locale.getJoinNewServerRestarting();
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.arguments().length != 2) {
            invocation.source().sendMessage(Component.text(usageMessage));
            return;
        }

        String targetServerId = invocation.arguments()[0];
        String groupName = invocation.arguments()[1];
        Player player = (Player) invocation.source();

        Optional<?> server = proxyServer.getServer(targetServerId);
        if (server.isEmpty()) {
            player.sendMessage(Component.text(MessageFormat.format(serverNotFoundMessage, targetServerId)));
            return;
        }

        if (!groupManager.hasGroup(groupName)) {
            player.sendMessage(Component.text(MessageFormat.format(groupNotFoundMessage, groupName)));
            return;
        }

        if (InstanceRestarter.isServerRestarting(targetServerId)) {
            player.sendMessage(Component.text(MessageFormat.format(serverRestartingMessage, targetServerId)));
            return;
        }

        redirectPlayerToTargetServer(player.getUniqueId(), targetServerId, groupName, player);
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
