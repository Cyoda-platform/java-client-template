package com.java_template.common.grpc.client.event_handling;

import io.cloudevents.v1.proto.CloudEvent;

/**
 * ABOUTME: Interface for sending CloudEvent messages through gRPC streams
 * with abstracted event transmission capabilities.
 */
public interface EventSender {
    void sendEvent(CloudEvent cloudEvent);
}

