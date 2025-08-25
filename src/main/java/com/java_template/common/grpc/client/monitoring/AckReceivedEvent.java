package com.java_template.common.grpc.client.monitoring;

public record AckReceivedEvent(
        String id,
        String sourceEventId,
        boolean success
) implements MonitoringEvent {}
