package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;
import io.cloudevents.CloudEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ValidateSubscriberProcessorTest {

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

        // Only EntityService may be mocked per requirements
        EntityService entityService = mock(EntityService.class);

        ValidateSubscriberProcessor processor = new ValidateSubscriberProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Subscriber that will pass isValid() and exercise normalization logic
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId("sub-123");
        subscriber.setEmail("  User@Example.COM  "); // will be normalized to lower-case trimmed
        subscriber.setName(" John Doe "); // will be trimmed
        subscriber.setFrequency("Weekly "); // will be trimmed and lower-cased
        subscriber.setStatus(" active "); // will be trimmed and upper-cased to ACTIVE

        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("sub-123");
        request.setProcessorName("ValidateSubscriberProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public CloudEvent getCloudEvent() { return null; }
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
        assertEquals("user@example.com", out.get("email").asText());
        assertEquals("John Doe", out.get("name").asText());
        assertEquals("weekly", out.get("frequency").asText());
        assertEquals("ACTIVE", out.get("status").asText());
        assertEquals("sub-123", out.get("subscriberId").asText());
    }
}