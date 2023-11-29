package net.onyxium.flexnet.instance;

import lombok.Builder;
import lombok.Getter;

import java.net.InetSocketAddress;

@Builder
@Getter
public class InstanceCreationResult {
    private final String instanceId;
    private final String instanceName;
    private final InetSocketAddress address;
    private final boolean success;
}
