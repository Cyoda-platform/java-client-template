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
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class UnsubscribeProcessorTest {

    @Test
    void sunnyDay_unsubscribe_active_subscriber_sets_unsubscribed() throws Exception {
        // Arrange - real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (does not require EntityService)
        UnsubscribeProcessor processor = new UnsubscribeProcessor(serializerFactory);

        // Build a valid Subscriber entity that passes isValid()
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail("user@example.com");
        subscriber.setName("Test User");
        subscriber.setInteractionsCount(0);
        subscriber.setStatus("ACTIVE"); // sunny path: ACTIVE -> UNSUBSCRIBED
        subscriber.setSubscribedAt(OffsetDateTime.now());

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        // Build request with payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("UnsubscribeProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext implementation
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
        assertTrue(response.getSuccess(), "Processing should be successful in sunny path");
        assertNotNull(response.getPayload(), "Response payload should be present");
        assertNotNull(response.getPayload().getData(), "Response payload data should be present");

        JsonNode resultData = response.getPayload().getData();
        // Check that status was changed to UNSUBSCRIBED by the processor
        assertEquals("UNSUBSCRIBED", resultData.get("status").asText(), "Subscriber status should be UNSUBSCRIBED after processing");
        // Also ensure email and name preserved
        assertEquals("user@example.com", resultData.get("email").asText());
        assertEquals("Test User", resultData.get("name").asText());
    }
}