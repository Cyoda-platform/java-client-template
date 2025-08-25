package com.java_template.common.grpc.client.monitoring;

public record KeepAliveReceivedEvent(
        Long timestamp
) implements MonitoringEvent {}
