package com.java_template.common.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.springframework.stereotype.Component;

@Component
public class CyodaContextFactory {

    private final ObjectMapper objectMapper;

    public CyodaContextFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T extends BaseEvent> CyodaEventContext<T> createCyodaEventContext(
            CloudEvent cloudEvent,
            Class<T> eventClass
    ) {
        return new CyodaEventContext<T>() {
            @Override
            public CloudEvent getCloudEvent() {
                return cloudEvent;
            }

            @Override
            public T getEvent() throws JsonProcessingException {
                return objectMapper.readValue(cloudEvent.getTextData(), eventClass);
            }
        };
    }


}
