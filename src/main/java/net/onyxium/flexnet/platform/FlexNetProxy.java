package net.onyxium.flexnet.platform;

import net.onyxium.flexnet.group.FlexNetGroup;

import java.net.InetSocketAddress;

public interface FlexNetProxy {

    void addServer(String identifier, InetSocketAddress address, FlexNetGroup group);
    void removeServer(String identifier, FlexNetGroup group);

    void scheduleTask(Runnable runnable, long delayInSecond);
    void scheduleRepeatTask(Runnable runnable, long delayInSecond, long intervalInSecond);

}
