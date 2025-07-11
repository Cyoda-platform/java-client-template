package com.java_template.common.workflow;

import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.BaseEvent;

/**
 * The Context to pass to CyodaProcessor and CyodaCriterion for workflow processing or criteria checking.
 * @param <T>
 */
public interface CyodaEventContext<T extends BaseEvent>  {
    CloudEvent getCloudEvent();
    T getEvent();

    // TODO: add gRPC access object to Cyoda
}
