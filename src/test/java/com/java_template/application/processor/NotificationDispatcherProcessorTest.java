package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class NotificationDispatcherProcessorTest {

    @Test
    void sunnyDay_email_contact_marks_lastNotifiedAt() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService may be mocked per requirements; constructor requires it
        EntityService entityService = mock(EntityService.class);

        NotificationDispatcherProcessor processor = new NotificationDispatcherProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid Subscriber entity for the sunny path
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId("sub-1");
        subscriber.setContactAddress("user@example.com");
        subscriber.setContactType("EMAIL"); // choose EMAIL to avoid HTTP call
        subscriber.setActive(true);

        // Convert entity to JsonNode using processor serializer to ensure expected format
        JsonNode entityJson;
        if (processorSerializer.getClass().getMethod("entityToJsonNode", Object.class) != null) {
            // use entityToJsonNode if available
            entityJson = (JsonNode) processorSerializer.getClass()
                    .getMethod("entityToJsonNode", Object.class)
                    .invoke(processorSerializer, subscriber);
        } else {
            // fallback
            entityJson = objectMapper.valueToTree(subscriber);
        }

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        // Provide an entityId that is not a UUID so processor will not successfully load a Job (safe sunny path)
        request.setEntityId("not-a-uuid");
        request.setProcessorName("NotificationDispatcherProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("sub-1", out.get("subscriberId").asText());
        // EMAIL path should set lastNotifiedAt to a non-null string
        assertTrue(out.hasNonNull("lastNotifiedAt"));
    }
}