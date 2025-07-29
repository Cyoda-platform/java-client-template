package com.java_template.common.grpc.client_v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.v1.proto.CloudEvent;
import java.util.Optional;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CloudEventParser {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ObjectMapper objectMapper;

    public CloudEventParser(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <EVENT_TYPE extends BaseEvent> Optional<EVENT_TYPE> parseCloudEvent(
            CloudEvent cloudEvent,
            Class<EVENT_TYPE> clazz
    ) {
        try {
            return Optional.of(
                    objectMapper.readValue(
                        cloudEvent.getTextData(),
                        clazz
                    )
            );
        } catch (JsonProcessingException e) {
            log.error(
                    "Error parsing cloud event. This shouldn't happen unless the systems are misaligned {}",
                    cloudEvent,
                    e
            );
            return Optional.empty();
        }
    }

}
