package com.java_template.common.grpc.client.event_handling;

import io.cloudevents.v1.proto.CloudEvent;

public interface EventSender {
    void sendEvent(CloudEvent cloudEvent);
}

