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
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class SubscriberVerificationProcessorTest {

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

        // EntityService is required by constructor; mock it but no interactions are expected in sunny email path
        EntityService entityService = mock(EntityService.class);

        SubscriberVerificationProcessor processor =
                new SubscriberVerificationProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Subscriber JSON payload for the sunny "email" path
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", UUID.randomUUID().toString());
        data.put("active", false); // boolean must be present for isValid()
        data.put("contactDetail", "user@example.com");
        data.put("contactType", "email");
        data.put("createdAt", "2025-01-01T00:00:00Z");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("SubscriberVerificationProcessor");
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
        // For email verification the processor keeps the subscriber inactive (manual verification).
        assertTrue(out.has("contactType"));
        assertEquals("email", out.get("contactType").asText());
        assertTrue(out.has("active"));
        assertFalse(out.get("active").asBoolean());
    }
}