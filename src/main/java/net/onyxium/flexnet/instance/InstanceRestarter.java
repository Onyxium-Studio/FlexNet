package net.onyxium.flexnet.instance;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.onyxium.flexnet.command.JoinNewCommand;
import net.onyxium.flexnet.config.FlexNetConfig;
import net.onyxium.flexnet.config.LocaleConfig;
import net.onyxium.flexnet.group.FlexNetGroup;
import net.onyxium.flexnet.group.FlexNetGroupManager;
import net.onyxium.flexnet.platform.FlexNetProxy;
import net.onyxium.flexnet.platform.velocity.FlexNetVelocityInstanceController;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class InstanceRestarter {
    private final FlexNetProxy proxy;
    private final FlexNetGroupManager groupManager;
    private final FlexNetVelocityInstanceController instanceController;
    private final InstanceManager instanceManager;
    private final JoinNewCommand joinNewCommand;
    private final FlexNetConfig config;
    private final LocaleConfig locale;
    private static final HashMap<String, Long> serverUptime = new HashMap<>();
    // server is wait for kick player and deleting
    private static final HashMap<String, Boolean> serversRestarting = new HashMap<>();
    // which server is in the process of creating new instance
    private static final HashMap<String, Boolean> serversRestartingProcess = new HashMap<>();

    public InstanceRestarter(FlexNetProxy proxy, FlexNetGroupManager groupManager, InstanceManager instanceManager,
                             FlexNetVelocityInstanceController instanceController, FlexNetConfig config,
                             JoinNewCommand joinNewCommand) {
        this.proxy = proxy;
        this.groupManager = groupManager;
        this.instanceManager = instanceManager;
        this.instanceController = instanceController;
        this.config = config;
        this.locale = config.getLocale();
        this.joinNewCommand = joinNewCommand;
    }

    public static void trackServer(String serverId) {
        serverUptime.put(serverId, System.currentTimeMillis());
    }

    public void checkAndRestartServers() {
        groupManager.getAllGroups().forEach(this::processGroupForRestart);
    }

    private void processGroupForRestart(FlexNetGroup group) {
        group.getAllServers().stream()
                .filter(entry -> !isServerRestarting(entry.getKey()))
                .forEach(entry -> checkServerForRestart(entry.getKey(), entry.getValue(), group));
    }

    private void checkServerForRestart(String serverId, RegisteredServer server, FlexNetGroup group) {
        long uptime = getServerUptime(serverId);
        int restartInterval = group.getAutoRestartInterval();

        if (uptime >= restartInterval) {
            initiateRestartProcess(serverId, group);
        }
    }

    private long getServerUptime(String serverId) {
        Long startTime = serverUptime.get(serverId);
        if (startTime == null) {
            log.error("Server {} not found in serverUptime", serverId);
            return 0;
        }
        long serverUptimeValue = (System.currentTimeMillis() - startTime) / (60 * 1000);
        log.info("Server {} uptime: {} minutes", serverId, serverUptimeValue);
        return serverUptimeValue;
    }

    private void initiateRestartProcess(String serverId, FlexNetGroup group) {
        if (serversRestartingProcess.getOrDefault(serverId, false)) {
            log.error("Restart process for server {} is already in progress", serverId);
            return;
        }
        serversRestartingProcess.put(serverId, true);

        CompletableFuture<String> future = instanceController.createInstance(
                config.getTemplates().get(group.getId()), group);

        future.thenAccept(newServerId -> handleServerRestart(serverId, group, newServerId));
    }

    private void handleServerRestart(String serverId, FlexNetGroup group, String newServerId) {
        markServerRestarting(serverId);
        scheduleRestartReminders(serverId, group, newServerId);

        long firstWarningTime = group.getRestartWarningIntervals()[0];
        log.info("Kicking players of server {} in {} seconds", serverId, firstWarningTime);

        CompletableFuture<Void> kickFuture = CompletableFuture.runAsync(() ->
                        kickPlayersGradually(newServerId, serverId, group),
                CompletableFuture.delayedExecutor(firstWarningTime, TimeUnit.SECONDS));

        kickFuture.thenRun(() -> deleteServerAfterWait(serverId, group, group.getPostShutdownWait()));
    }

    private void deleteServerAfterWait(String serverId, FlexNetGroup group, int waitTime) {
        log.info("Deleting server {} in {} minutes", serverId, waitTime);
        proxy.scheduleTask(() -> {
            if (group.getServer(serverId) != null) {
                instanceController.removeInstanceId(serverId);
                serversRestartingProcess.remove(serverId);
                serversRestarting.remove(serverId);
                serverUptime.remove(serverId);
                proxy.removeServer(serverId, group);
                instanceManager.deleteInstance(serverId, (b) -> {});
            }
        }, waitTime * 60L);
    }

    private void kickPlayersGradually(String newServerId, String serverId, FlexNetGroup group) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        RegisteredServer server = group.getServer(serverId);
        String groupName = group.getServerName();

        if (server != null) {
            kickPlayers(server, newServerId, groupName, future);
        } else {
            log.info("Server {} not found for kicking players", serverId);
            future.complete(null);
        }

        future.thenRun(() -> log.info("Kicking players done"));
    }

    private void kickPlayers(RegisteredServer server, String newServerId, String groupName, CompletableFuture<Void> future) {
        if (!server.getPlayersConnected().isEmpty()) {
            log.info("Kicking players of server {}", server.getServerInfo().getName());

            server.getPlayersConnected().stream().limit(5).forEach(player -> {
                UUID playerId = player.getUniqueId();
                joinNewCommand.redirectPlayerToTargetServer(playerId, newServerId, groupName, player);
            });

            proxy.scheduleTask(() -> kickPlayers(server, newServerId, groupName, future), 3);
        } else {
            future.complete(null);
        }
    }


    private void scheduleRestartReminders(String serverId, FlexNetGroup group, String newServerId) {
        int[] intervals = group.getRestartWarningIntervals();
        int firstWarningTime = intervals[0];

        for (int interval : intervals) {
            long delay = firstWarningTime - interval;
            if (delay >= 0) {
                proxy.scheduleTask(() -> notifyPlayersOfRestart(serverId, group, newServerId, interval), delay);
            }
        }
    }

    private void notifyPlayersOfRestart(String serverId, FlexNetGroup group, String newServerId, int leftTime) {
        RegisteredServer server = group.getServer(serverId);
        if (server != null) {
            // TODO: With the button, player can click and connect to the new server
            String restartMessageTemplate = locale.getServerRestartWarning();
            String restartMessage = restartMessageTemplate.replace("{0}", String.valueOf(leftTime));

            TextComponent.Builder messageBuilder = Component.text()
                    .content(restartMessage)
                    .append(Component.text(" [Click to join new server]")
                            .color(TextColor.fromHexString("#00A5FF"))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to join new server")))
                            .clickEvent(ClickEvent.runCommand("/JoinNew " + newServerId + " " + group.getServerName())));
            Component message = messageBuilder.build();

            server.getPlayersConnected().forEach(player -> player.sendMessage(message));
            log.info("Notified players of server {} restart in {} seconds", serverId, leftTime);
        } else {
            log.error("Server {} not found for notification", serverId);
        }
    }

    public void markServerRestarting(String serverId) {
        serversRestarting.put(serverId, true);
    }

    public static boolean isServerRestarting(String serverId) {
        return serversRestarting.getOrDefault(serverId, false);
    }

}
