package com.java_template.common.grpc.client.monitoring;

import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.processing.EventAckResponse;

/**
 * ABOUTME: Interface for tracking gRPC event lifecycle including sent events,
 * acknowledgments, keep-alives, and greet messages for monitoring purposes.
 */
public interface EventTracker {
    void trackEventSent(CloudEvent event);
    void trackAcknowledgeReceived(EventAckResponse acknowledgeResponse);
    void trackKeepAlive(Long eventTimestamp);
    void trackGreetReceived();
}
