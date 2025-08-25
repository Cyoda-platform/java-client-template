package com.java_template.common.grpc.client.monitoring;

import java.util.List;

public record SentEventsWithoutAckGrowingEvent(
        List<String> events
) implements MonitoringEvent {}
