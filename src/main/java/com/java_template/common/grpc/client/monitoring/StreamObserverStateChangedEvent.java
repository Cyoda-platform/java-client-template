package com.java_template.common.grpc.client.monitoring;

/**
 * ABOUTME: Monitoring event record representing stream observer state transitions
 * with old and new observer state tracking.
 */
public record StreamObserverStateChangedEvent(
        ObserverState oldState,
        ObserverState newState
) implements MonitoringEvent {}
