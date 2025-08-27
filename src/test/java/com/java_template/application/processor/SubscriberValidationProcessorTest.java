package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class SubscriberValidationProcessorTest {

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

        // Only EntityService is mocked per requirements
        EntityService entityService = mock(EntityService.class);

        SubscriberValidationProcessor processor = new SubscriberValidationProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid Subscriber JSON payload according to Subscriber.isValid()
        ObjectNode contactDetails = objectMapper.createObjectNode();
        contactDetails.put("url", "mailto:example@example.com");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", "sub-1");
        data.put("contactType", "email");
        data.put("createdAt", "2020-01-01T00:00:00Z");
        data.put("active", true);
        data.set("contactDetails", contactDetails);
        // verified must be non-null for isValid()
        data.put("verified", false);

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("sub-1");
        request.setProcessorName("SubscriberValidationProcessor");
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
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Core happy-path expectation: verified remains present and false for non-webhook contact types
        assertTrue(out.has("verified"));
        assertFalse(out.get("verified").asBoolean());
        // Basic sanity: id preserved
        assertEquals("sub-1", out.get("id").asText());
        // contactType preserved
        assertEquals("email", out.get("contactType").asText());
    }
}