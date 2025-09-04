package com.java_template.common.workflow;

import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.BaseEvent;

/**
 * ABOUTME: Context interface for passing CloudEvent and BaseEvent data to
 * CyodaProcessor and CyodaCriterion components during workflow execution.
 */
public interface CyodaEventContext<T extends BaseEvent>  {
    CloudEvent getCloudEvent();
    T getEvent();

    // TODO: add gRPC access object to Cyoda
}
