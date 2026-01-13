package com.java_template.common.grpc.client.connection;

import java.util.function.Consumer;

/**
 * ABOUTME: Strategy interface for handling gRPC connection reconnection logic
 * with configurable retry policies and backoff strategies.
 */
public interface ReconnectionStrategy {
    void reset();
    void requestReconnection(Runnable reconnect);
    void resurrect(Runnable reconnect);  // Manually trigger resurrection
    void addIdleStateListener(Consumer<Boolean> listener);  // Notify when entering/exiting idle
}

