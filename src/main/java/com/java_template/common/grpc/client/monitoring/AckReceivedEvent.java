package com.java_template.common.grpc.client.monitoring;

/**
 * ABOUTME: Monitoring event record representing acknowledgment receipt
 * with event correlation and success status tracking.
 */
public record AckReceivedEvent(
        String id,
        String sourceEventId,
        boolean success
) implements MonitoringEvent {}
