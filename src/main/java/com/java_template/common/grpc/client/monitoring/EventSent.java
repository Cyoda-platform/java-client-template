package com.java_template.common.grpc.client.monitoring;

/**
 * ABOUTME: Monitoring event record representing sent events
 * with event identification and type tracking.
 */
public record EventSent(
        String eventId,
        String eventType
) implements MonitoringEvent {}
