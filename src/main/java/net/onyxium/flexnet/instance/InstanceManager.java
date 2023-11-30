package net.onyxium.flexnet.instance;

import net.onyxium.flexnet.model.InstanceTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface InstanceManager {

    void createInstance(InstanceTemplate template, Consumer<InstanceCreationResult> callback);
    void deleteInstance(String identifier, Consumer<Boolean> callback);

}
