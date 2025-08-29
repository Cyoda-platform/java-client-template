package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InitEmailVerificationTest {

    @Test
    void sunnyDay_process_test() {
        // Setup real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only the EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor with real serializerFactory and mocked EntityService
        InitEmailVerification processor = new InitEmailVerification(serializerFactory, entityService, objectMapper);

        // Prepare a valid User entity that will pass isValid() and trigger marketing consent creation
        User user = new User();
        user.setUserId("user-123");
        user.setEmail("user@example.com");
        user.setMarketingEnabled(true);
        // leave emailVerified null to ensure processor sets it to false
        // no auditRefs initially

        JsonNode userJson = objectMapper.valueToTree(user);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("InitEmailVerification");
        DataPayload payload = new DataPayload();
        payload.setData(userJson);
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
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "processing should be successful");

        // Inspect returned payload data for expected sunny-day state changes
        assertNotNull(response.getPayload(), "payload should be present");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "result data must be present");

        // emailVerified should be explicitly false
        assertTrue(resultData.has("emailVerified"), "emailVerified field must be present");
        assertFalse(resultData.get("emailVerified").asBoolean(), "emailVerified should be false");

        // gdprState should be set to "email_unverified"
        assertTrue(resultData.has("gdprState"), "gdprState must be present");
        assertEquals("email_unverified", resultData.get("gdprState").asText(), "gdprState should be 'email_unverified'");

        // auditRefs should contain the appended audit id
        assertTrue(resultData.has("auditRefs"), "auditRefs must be present");
        JsonNode auditRefsNode = resultData.get("auditRefs");
        assertTrue(auditRefsNode.isArray(), "auditRefs should be an array");
        assertEquals(1, auditRefsNode.size(), "one audit ref should have been appended");
        assertTrue(auditRefsNode.get(0).asText().length() > 0, "audit ref should be a non-empty string");

        // Verify EntityService.addItem was called at least once (for Audit) and again for Consent due to marketingEnabled=true
        verify(entityService, atLeast(1)).addItem(eq("Audit"), eq(1), any());
        verify(entityService, atLeast(1)).addItem(eq("Consent"), eq(1), any());
    }
}