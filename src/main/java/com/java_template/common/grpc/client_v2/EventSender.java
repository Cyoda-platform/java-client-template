package com.java_template.common.grpc.client_v2;

import io.cloudevents.v1.proto.CloudEvent;

interface EventSender {
    void sendEvent(CloudEvent cloudEvent);
}

