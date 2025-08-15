package com.java_template.common.grpc.client.event_handling;

import io.cloudevents.v1.proto.CloudEvent;
import java.util.Set;

public interface EventHandler {
    void handleEvent(CloudEvent cloudEvent);

    Set<String> getSupportedTags();
}
