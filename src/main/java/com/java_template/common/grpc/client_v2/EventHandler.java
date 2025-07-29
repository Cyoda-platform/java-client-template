package com.java_template.common.grpc.client_v2;

import io.cloudevents.v1.proto.CloudEvent;
import java.util.Set;

interface EventHandler {
    void handleEvent(CloudEvent cloudEvent);

    Set<String> getSupportedTags();
}
