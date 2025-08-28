package com.java_template.common.grpc.client.connection;

import java.util.concurrent.CompletableFuture;

public interface GreetEventListener {
    void registerPendingGreetEvent(String joinEventId, CompletableFuture<String> greetPromise);
}
