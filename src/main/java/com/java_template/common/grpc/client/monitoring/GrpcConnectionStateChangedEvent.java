package com.java_template.common.grpc.client.monitoring;

import io.grpc.ConnectivityState;

/**
 * ABOUTME: Monitoring event record representing gRPC connection state transitions
 * with old and new connectivity state tracking.
 */
public record GrpcConnectionStateChangedEvent(
        ConnectivityState oldState,
        ConnectivityState newState
) implements MonitoringEvent {}
