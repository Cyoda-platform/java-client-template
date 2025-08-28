package com.java_template.common.grpc.client.monitoring;

import io.grpc.ConnectivityState;

public record GrpcConnectionStateChangedEvent(
        ConnectivityState oldState,
        ConnectivityState newState
) implements MonitoringEvent {}
