package com.java_template.common.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Factory component for creating CyodaEventContext instances from CloudEvent
 * and BaseEvent data for workflow processing and criteria evaluation.
 */
@Component
public class CyodaContextFactory {

    private final ObjectMapper objectMapper;

    public CyodaContextFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T extends BaseEvent> CyodaEventContext<T> createCyodaEventContext(
            CloudEvent cloudEvent,
            Class<T> eventClass
    )  throws JsonProcessingException {
        T event = objectMapper.readValue(cloudEvent.getTextData(), eventClass);

        return new CyodaEventContext<T>() {
            @Override
            public CloudEvent getCloudEvent() {
                return cloudEvent;
            }

            @Override
            public @NotNull T getEvent() {
                return event;
            }
        };
    }


}
