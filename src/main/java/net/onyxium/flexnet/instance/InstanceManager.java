package net.onyxium.flexnet.instance;

import net.onyxium.flexnet.model.InstanceTemplate;

import java.util.concurrent.CompletableFuture;

public interface InstanceManager {

    CompletableFuture<InstanceCreationResult> createInstance(InstanceTemplate template);

}
