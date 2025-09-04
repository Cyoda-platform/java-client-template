package com.java_template.common.grpc.client.monitoring;

import io.grpc.ConnectivityState;

/**
 * ABOUTME: Interface for providing access to the last known gRPC connection
 * state for monitoring and health check purposes.
 */
public interface GrpcConnectionStateProvider {
    ConnectivityState getLastKnownState();
}
