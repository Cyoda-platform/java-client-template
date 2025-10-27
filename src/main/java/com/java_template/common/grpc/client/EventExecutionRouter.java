package com.java_template.common.grpc.client;

import org.cyoda.cloud.api.event.common.CloudEventType;

/**
 * ABOUTME: Router interface for dispatching event processing tasks to appropriate
 * thread pools based on event type (processor, criteria, or control events).
 */
public interface EventExecutionRouter {
    /**
     * Routes the given task to the appropriate executor based on the event type.
     * 
     * @param eventType The type of CloudEvent being processed
     * @param task The task to execute
     */
    void routeAndExecute(CloudEventType eventType, Runnable task);
}

