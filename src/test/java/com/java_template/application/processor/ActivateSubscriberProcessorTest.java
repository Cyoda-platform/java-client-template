package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ActivateSubscriberProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        ActivateSubscriberProcessor processor = new ActivateSubscriberProcessor(serializerFactory);

        // Create a valid Subscriber entity that passes isValid() and has an email that should mark it active
        Subscriber subscriber = new Subscriber();
        subscriber.setId("sub-" + UUID.randomUUID());
        subscriber.setActive(Boolean.FALSE); // initial state; processor should set to true
        subscriber.setCreatedAt("2025-08-01T12:00:00Z");
        subscriber.setEmail("user@example.com"); // valid simple email for processor logic
        subscriber.setName("Test Subscriber");
        subscriber.setFilters("filter-expression");

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId(subscriber.getId());
        request.setProcessorName("ActivateSubscriberProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Processor should set active to true when email is valid
        assertTrue(out.has("active"));
        assertTrue(out.get("active").asBoolean());
        // Ensure other core fields remain present
        assertEquals(subscriber.getId(), out.get("id").asText());
        assertEquals(subscriber.getEmail(), out.get("email").asText());
    }
}