package com.java_template.common.grpc.client.monitoring;

public record StreamObserverStateChangedEvent(
        ObserverState oldState,
        ObserverState newState
) implements MonitoringEvent {}
