package net.onyxium.flexnet.platform;

import com.velocitypowered.api.scheduler.ScheduledTask;
import net.onyxium.flexnet.group.FlexNetGroup;

import java.net.InetSocketAddress;

public interface FlexNetProxy {

    void addServer(String identifier, InetSocketAddress address, FlexNetGroup group);
    void removeServer(String identifier, FlexNetGroup group);

    void scheduleTask(Runnable runnable, long delayInSecond);

    void scheduleTask(Runnable runnable, long delay, boolean isMillisecond);

    ScheduledTask scheduleRepeatTask(Runnable runnable, long delayInSecond, long intervalInSecond);

}
