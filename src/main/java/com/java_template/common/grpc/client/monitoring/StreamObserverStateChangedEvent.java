package com.java_template.common.grpc.client.monitoring;

public record StreamObserverStateChangedEvent(
        ObserverState observerState
) implements MonitoringEvent {}
