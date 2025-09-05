package com.java_template.common.grpc.client.event_handling;

import io.cloudevents.v1.proto.CloudEvent;
import java.util.Set;

/**
 * ABOUTME: Interface for handling CloudEvent processing with tag-based
 * event routing and filtering capabilities.
 */
public interface EventHandler {
    void handleEvent(CloudEvent cloudEvent);

    Set<String> getSupportedTags();
}
