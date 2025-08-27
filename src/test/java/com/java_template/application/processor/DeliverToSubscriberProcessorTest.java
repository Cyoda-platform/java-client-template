package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class DeliverToSubscriberProcessorTest {

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

        DeliverToSubscriberProcessor processor = new DeliverToSubscriberProcessor(serializerFactory, objectMapper);

        ObjectNode entityJson = objectMapper.createObjectNode();
        // Fields chosen to satisfy Subscriber.isValid() and processor checks:
        // - subscriberId present
        // - active true
        // - contactType "email" to avoid real HTTP calls in test
        entityJson.put("subscriberId", "sub-123");
        entityJson.put("active", true);
        entityJson.put("contactType", "email");
        entityJson.put("preferredPayload", "minimal");

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r-" + UUID.randomUUID());
        request.setRequestId("req-" + UUID.randomUUID());
        request.setEntityId("e-" + UUID.randomUUID());
        request.setProcessorName("DeliverToSubscriberProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should be present");
        assertNotNull(response.getPayload().getData(), "Response payload data should be present");

        // Verify subscriberId is preserved and lastNotifiedAt was set by the processor
        assertEquals("sub-123", response.getPayload().getData().get("subscriberId").asText());
        assertTrue(response.getPayload().getData().get("active").asBoolean());
        assertNotNull(response.getPayload().getData().get("lastNotifiedAt"), "lastNotifiedAt should be set");
        assertFalse(response.getPayload().getData().get("lastNotifiedAt").asText().isBlank(), "lastNotifiedAt should be a non-blank timestamp");
    }
}