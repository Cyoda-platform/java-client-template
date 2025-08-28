package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ActivateUserProcessorTest {

    @Test
    void sunnyDay_activateUser_setsActiveStatus() throws Exception {
        // Setup real ObjectMapper configured per requirements
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor under test (no EntityService required)
        ActivateUserProcessor processor = new ActivateUserProcessor(serializerFactory);

        // Build a valid User entity that passes isValid() and is in PROFILE_VERIFIED state
        User user = new User();
        user.setUserId("user-123");
        user.setFullName("Jane Doe");
        user.setEmail("jane.doe@example.com");
        user.setRegisteredAt("2025-01-01T00:00:00Z");
        user.setStatus("PROFILE_VERIFIED");

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(user);

        // Build request with minimal required fields
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId("user-123");
        request.setProcessorName("ActivateUserProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response success
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        // Assert payload data reflects transition to Active
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data should not be null");

        User resulting = objectMapper.treeToValue(responseData, User.class);
        assertNotNull(resulting, "Deserialized resulting user should not be null");
        assertEquals("Active", resulting.getStatus(), "User status should have been transitioned to Active");
        // Ensure identity preserved
        assertEquals(user.getUserId(), resulting.getUserId(), "UserId should be preserved");
    }
}