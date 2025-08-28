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

public class DeactivateSubscriberProcessorTest {

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

        DeactivateSubscriberProcessor processor = new DeactivateSubscriberProcessor(serializerFactory);

        // Create a valid Subscriber entity that passes isValid()
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId(UUID.randomUUID().toString());
        subscriber.setEmail("user@example.com");
        subscriber.setFrequency("weekly");
        subscriber.setStatus("ACTIVE"); // initially active, processor should change to UNSUBSCRIBED

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId(subscriber.getSubscriberId());
        request.setProcessorName("DeactivateSubscriberProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Output data should not be null");

        // Verify that status was changed to UNSUBSCRIBED and subscriberId preserved
        assertEquals("UNSUBSCRIBED", out.get("status").asText(), "Subscriber status should be UNSUBSCRIBED");
        assertEquals(subscriber.getSubscriberId(), out.get("subscriberId").asText(), "subscriberId should be preserved");
    }
}