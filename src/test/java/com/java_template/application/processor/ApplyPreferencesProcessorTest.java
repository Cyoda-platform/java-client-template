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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ApplyPreferencesProcessorTest {

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

        // EntityService is required by the processor constructor; mock it but no stubbing needed for this sunny path
        EntityService entityService = mock(EntityService.class);

        ApplyPreferencesProcessor processor = new ApplyPreferencesProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Subscriber entity instance that will pass isValid()
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId("sub-123");
        subscriber.setEmail(" USER@Example.COM "); // will be trimmed & lowercased by processor
        subscriber.setName(" John Doe ");
        subscriber.setFilters(" area=NW ");
        subscriber.setFrequency(" Weekly ");
        subscriber.setStatus("PENDING"); // initial status; processor will update it

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("sub-123");
        request.setProcessorName("ApplyPreferencesProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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

        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Expected transformations: trimmed/lowercased email, trimmed name, trimmed/lowercased frequency, status set to PREFERENCES_APPLIED
        assertEquals("user@example.com", out.get("email").asText());
        assertEquals("John Doe", out.get("name").asText());
        assertEquals("weekly", out.get("frequency").asText());
        assertEquals("PREFERENCES_APPLIED", out.get("status").asText());
        // Ensure subscriberId preserved
        assertEquals("sub-123", out.get("subscriberId").asText());
    }
}