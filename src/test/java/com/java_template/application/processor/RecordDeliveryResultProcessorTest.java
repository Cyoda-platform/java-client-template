package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class RecordDeliveryResultProcessorTest {

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

        RecordDeliveryResultProcessor processor = new RecordDeliveryResultProcessor(serializerFactory);

        // Build a valid Subscriber payload: required fields id, name, contactDetails, contactType, active
        ObjectNode data = objectMapper.createObjectNode();
        String subscriberId = "sub-123";
        data.put("id", subscriberId);
        data.put("name", "Test Subscriber");
        data.put("contactDetails", "user@example.com"); // valid email for sunny path
        data.put("contactType", "email");
        data.put("active", true);

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r-" + UUID.randomUUID());
        request.setRequestId("req-" + UUID.randomUUID());
        request.setEntityId(subscriberId);
        request.setProcessorName("RecordDeliveryResultProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Response data should not be null");

        // Check core sunny-day state changes / preservation:
        // lastNotifiedAt should be set by processor (non-null, non-blank)
        assertTrue(out.has("lastNotifiedAt"), "lastNotifiedAt should be present");
        String lastNotifiedAt = out.get("lastNotifiedAt").asText();
        assertNotNull(lastNotifiedAt);
        assertFalse(lastNotifiedAt.isBlank(), "lastNotifiedAt should be non-blank");

        // active should remain true for valid email contact
        assertTrue(out.has("active"));
        assertTrue(out.get("active").asBoolean(), "Subscriber should remain active for valid email contact");

        // id and name should be preserved
        assertEquals(subscriberId, out.get("id").asText());
        assertEquals("Test Subscriber", out.get("name").asText());

        // Ensure it deserializes back to Subscriber and passes validation
        Subscriber deserialized = objectMapper.convertValue(out, Subscriber.class);
        assertNotNull(deserialized);
        assertTrue(deserialized.isValid(), "Deserialized Subscriber should be valid");
    }
}