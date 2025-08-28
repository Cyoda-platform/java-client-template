package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VerificationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and serializer factory
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService may be mocked per requirements
        EntityService entityService = mock(EntityService.class);

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        VerificationProcessor processor = new VerificationProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid Subscriber entity for the "email" delivery preference sunny path
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId("sub-123");
        subscriber.setName("Test Subscriber");
        subscriber.setContactEmail("test@example.com"); // valid email format should pass simple regex
        subscriber.setDeliveryPreference("email");
        subscriber.setActive(false); // must be non-null for validation; processor will set to true on success

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(UUID.randomUUID().toString());
        request.setProcessorName("VerificationProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Response payload data should not be null");
        // After processing happy-path email verification, active should be true
        assertTrue(resultData.has("active"), "Result data should contain 'active' field");
        assertTrue(resultData.get("active").asBoolean(), "Subscriber should be marked active after successful email verification");
    }
}