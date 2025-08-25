package com.java_template.common.grpc.client.monitoring;

public record EventSent(
        String eventId,
        String eventType
) implements MonitoringEvent {}
