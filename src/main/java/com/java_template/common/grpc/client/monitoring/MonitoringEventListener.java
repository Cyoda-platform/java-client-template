package com.java_template.common.grpc.client.monitoring;

/**
 * ABOUTME: Generic listener interface for handling monitoring events
 * with type-safe event processing and filtering capabilities.
 */
public interface MonitoringEventListener<MONITORING_EVENT_TYPE extends MonitoringEvent> {
    void handle(MONITORING_EVENT_TYPE monitoringEvent);

    Class<MONITORING_EVENT_TYPE> getEventType();
}
