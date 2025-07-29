package com.java_template.common.grpc.client_v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.java_template.common.config.Config;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.v1.proto.CloudEvent;
import java.net.URI;
import java.util.UUID;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.springframework.stereotype.Component;

@Component
class EventBuilder {
    private static final String EVENT_SOURCE_URI = "urn:cyoda:calculation-member:" + Config.GRPC_PROCESSOR_TAG; // TODO: Move to props

    private final ObjectMapper objectMapper;
    private final EventFormat eventFormat;

    EventBuilder(
            final ObjectMapper objectMapper,
            final EventFormat eventFormat
    ) {
        this.objectMapper = objectMapper;
        this.eventFormat = eventFormat;
    }

    public CloudEvent buildEvent(final BaseEvent event) throws InvalidProtocolBufferException {
        // TODO: Do we really need to build CloudEvent -> serialize -> parseFrom ???
        return CloudEvent.parseFrom(
                eventFormat.serialize(
                        CloudEventBuilder.v1()
                                .withSource(URI.create(EVENT_SOURCE_URI))
                                .withType(event.getClass().getSimpleName())
                                .withId(UUID.randomUUID().toString())
                                .withData(PojoCloudEventData.wrap(event, this::mapEvent))
                                .build()
                )
        );
    }

    private byte[] mapEvent(final BaseEvent eventData) {
        try {
            return objectMapper.writeValueAsBytes(eventData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing event data", e);
        }
    }

}
