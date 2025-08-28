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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class DeliveryTestProcessorTest {

    @Test
    void sunnyDay_emailPreference_marksActiveTrue() throws Exception {
        // Arrange - ObjectMapper and real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService may be mocked per requirements
        EntityService entityService = mock(EntityService.class);

        // Create processor with real serializerFactory, mocked EntityService, real ObjectMapper
        DeliveryTestProcessor processor = new DeliveryTestProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid Subscriber entity for the sunny day (email path)
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId("sub-123");
        subscriber.setName("Sunny Subscriber");
        subscriber.setContactEmail("user@example.com"); // contains '@' => email path will set active=true
        subscriber.setDeliveryPreference("email");
        subscriber.setWebhookUrl(null);
        subscriber.setActive(false); // initial state false, processor should set true

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(subscriber);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DeliveryTestProcessor");
        DataPayload payload = new DataPayload();
        // DataPayload is expected to accept setData()
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
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response payload data should not be null");

        Subscriber result = objectMapper.treeToValue(responseData, Subscriber.class);
        assertNotNull(result, "Deserialized subscriber should not be null");
        assertTrue(result.getActive(), "Subscriber should be marked active by email delivery test");
        // Minimal additional check to ensure the same subscriberId was preserved
        assertEquals(subscriber.getSubscriberId(), result.getSubscriberId());
    }
}