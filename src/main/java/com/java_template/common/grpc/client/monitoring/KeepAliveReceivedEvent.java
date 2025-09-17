package com.java_template.common.grpc.client.monitoring;

/**
 * ABOUTME: Monitoring event record representing keep-alive message receipt
 * with timestamp tracking for connection health monitoring.
 */
public record KeepAliveReceivedEvent(
        Long timestamp
) implements MonitoringEvent {}
