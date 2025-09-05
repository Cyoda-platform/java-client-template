package com.java_template.common.grpc.client.connection;

/**
 * ABOUTME: Strategy interface for handling gRPC connection reconnection logic
 * with configurable retry policies and backoff strategies.
 */
public interface ReconnectionStrategy {
    void reset();
    void requestReconnection(Runnable reconnect);
}

