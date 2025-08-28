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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ValidateContactProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        ValidateContactProcessor processor = new ValidateContactProcessor(serializerFactory);

        // Build a valid Subscriber entity that passes isValid() and has a valid email (so processor should set active=true)
        Subscriber subscriber = new Subscriber();
        subscriber.setId("sub-1");
        subscriber.setActive(false); // initial value; processor should update this
        subscriber.setCreatedAt("2025-01-01T00:00:00Z");
        subscriber.setEmail("user@example.com"); // valid email for sunny path
        subscriber.setFilters("{}"); // non-blank as required by isValid()
        subscriber.setName("Test Subscriber");
        subscriber.setWebhookUrl(null);

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId(subscriber.getId());
        request.setProcessorName("ValidateContactProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should succeed for sunny-day valid subscriber");

        assertNotNull(response.getPayload(), "Response payload should be present");
        Object dataObj = response.getPayload().getData();
        assertNotNull(dataObj, "Response payload data should not be null");
        assertTrue(dataObj instanceof JsonNode, "Payload data should be a JsonNode");

        JsonNode out = (JsonNode) dataObj;
        // Processor should have set active = true because email is valid
        assertTrue(out.has("active") && out.get("active").asBoolean(), "Subscriber active flag should be set to true");

        // Ensure email preserved
        assertTrue(out.has("email") && "user@example.com".equals(out.get("email").asText()));
        // Ensure id preserved
        assertTrue(out.has("id") && subscriber.getId().equals(out.get("id").asText()));
    }
}