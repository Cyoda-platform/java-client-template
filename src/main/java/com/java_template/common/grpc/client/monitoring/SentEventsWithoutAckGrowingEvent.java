package com.java_template.common.grpc.client.monitoring;

import java.util.List;

/**
 * ABOUTME: Monitoring event record representing growing list of sent events
 * without acknowledgments for connection health and performance monitoring.
 */
public record SentEventsWithoutAckGrowingEvent(
        List<String> events
) implements MonitoringEvent {}
