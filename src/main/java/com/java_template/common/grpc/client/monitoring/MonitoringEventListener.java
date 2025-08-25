package com.java_template.common.grpc.client.monitoring;

public interface MonitoringEventListener<MONITORING_EVENT_TYPE extends MonitoringEvent> {
    void handle(MONITORING_EVENT_TYPE monitoringEvent);

    Class<MONITORING_EVENT_TYPE> getEventType();
}
