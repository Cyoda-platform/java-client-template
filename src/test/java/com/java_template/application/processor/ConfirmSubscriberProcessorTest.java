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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ConfirmSubscriberProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper configured to ignore unknown properties
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        ConfirmSubscriberProcessor processor = new ConfirmSubscriberProcessor(serializerFactory);

        // Prepare a valid Subscriber entity in PENDING_CONFIRMATION state
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail("test@example.com");
        subscriber.setName("Test User");
        subscriber.setStatus("PENDING_CONFIRMATION");
        subscriber.setInteractionsCount(0);
        subscriber.setSubscribedAt(OffsetDateTime.now());

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ConfirmSubscriberProcessor");
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

        // Assert - sunny day expectations
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");
        assertNotNull(response.getPayload(), "Response payload should be present");
        assertNotNull(response.getPayload().getData(), "Response payload data should be present");

        JsonNode responseData = response.getPayload().getData();
        assertEquals("ACTIVE", responseData.get("status").asText(), "Subscriber status should be ACTIVE after confirmation");
        assertTrue(responseData.has("interactionsCount"), "interactionsCount should be present");
        assertEquals(0, responseData.get("interactionsCount").asInt(), "interactionsCount should remain 0");
        assertTrue(responseData.has("subscribedAt"), "subscribedAt should be present after processing");
    }
}