package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.*;

public class CreateDummyPaymentProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Setup ObjectMapper and serializers (real objects)
        ObjectMapper objectMapper = new ObjectMapper();
        // Ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService required by this processor)
        CreateDummyPaymentProcessor processor = new CreateDummyPaymentProcessor(serializerFactory);

        // Build minimal payload that satisfies the processor's validation (cartId non-blank and amount non-negative)
        JsonNode payloadNode = objectMapper.createObjectNode()
                .put("cartId", "cart-123")
                .put("amount", 42.5);

        DataPayload payload = new DataPayload();
        payload.setData(payloadNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CreateDummyPaymentProcessor");
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
        assertNotNull(response, "response should not be null");
        assertTrue(Boolean.TRUE.equals(response.getSuccess()), "response should indicate success");

        assertNotNull(response.getPayload(), "response payload should not be null");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "result data should not be null");

        // Core happy-path expectations set by processor.processEntityLogic:
        // - provider set to "DUMMY"
        // - status set to "INITIATED"
        // - paymentId generated (non-blank)
        // - createdAt and updatedAt present (non-blank)
        // - amount preserved
        assertEquals("DUMMY", resultData.path("provider").asText(), "provider should be DUMMY");
        assertEquals("INITIATED", resultData.path("status").asText(), "status should be INITIATED");
        String paymentId = resultData.path("paymentId").asText(null);
        assertNotNull(paymentId);
        assertFalse(paymentId.isBlank(), "paymentId should be generated and non-blank");
        String createdAt = resultData.path("createdAt").asText(null);
        String updatedAt = resultData.path("updatedAt").asText(null);
        assertNotNull(createdAt);
        assertFalse(createdAt.isBlank(), "createdAt should be set");
        assertNotNull(updatedAt);
        assertFalse(updatedAt.isBlank(), "updatedAt should be set");
        // amount preserved
        assertEquals(42.5, resultData.path("amount").asDouble(), 0.0001);
        // cartId preserved
        assertEquals("cart-123", resultData.path("cartId").asText());
    }
}