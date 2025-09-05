package com.java_template.common.grpc.client.connection;

import java.util.concurrent.CompletableFuture;

/**
 * ABOUTME: Listener interface for handling greet events during connection establishment
 * with promise-based asynchronous completion tracking.
 */
public interface GreetEventListener {
    void registerPendingGreetEvent(String joinEventId, CompletableFuture<String> greetPromise);
}
