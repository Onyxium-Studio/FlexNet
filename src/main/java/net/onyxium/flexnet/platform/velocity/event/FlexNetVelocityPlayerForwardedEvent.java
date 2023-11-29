package net.onyxium.flexnet.platform.velocity.event;

import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.onyxium.flexnet.group.FlexNetGroup;

@AwaitingEvent
@AllArgsConstructor
@Getter
public class FlexNetVelocityPlayerForwardedEvent {

    private final Player player;
    private final FlexNetGroup group;
    private final RegisteredServer server;

}
