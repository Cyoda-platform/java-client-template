package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
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

import static org.junit.jupiter.api.Assertions.*;

public class VerifyProfileProcessorTest {

    @Test
    void sunnyDay_verifyProfile_process_test() {
        // Arrange - ObjectMapper and serializers (real, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (uses only serializerFactory)
        VerifyProfileProcessor processor = new VerifyProfileProcessor(serializerFactory);

        // Build a valid User entity that passes isValid() and satisfies sunny-path checks
        User user = new User();
        user.setUserId("user-123");
        user.setFullName("Jane Doe");
        user.setEmail("jane.doe@example.com");
        user.setRegisteredAt("2024-01-01T12:00:00Z");
        user.setStatus("REGISTERED");
        user.setPhone("+1-555-123-4567"); // contains >=7 digits so phoneValid true

        JsonNode entityJson = objectMapper.valueToTree(user);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("VerifyProfileProcessor");
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
        assertTrue(response.getSuccess(), "Processing should succeed on sunny path");

        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Result payload data must be present");

        // Verify status advanced to PROFILE_VERIFIED on successful identity verification
        assertEquals("PROFILE_VERIFIED", resultData.get("status").asText(), "User status should be PROFILE_VERIFIED");

        // Verify preferences contain verification details
        JsonNode prefs = resultData.get("preferences");
        assertNotNull(prefs, "Preferences should be present");
        assertTrue(prefs.get("identityVerified").asBoolean(), "identityVerified should be true");
        assertTrue(prefs.get("emailValid").asBoolean(), "emailValid should be true");
        assertTrue(prefs.get("phoneValid").asBoolean(), "phoneValid should be true");
    }
}