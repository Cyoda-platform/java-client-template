package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.consent.version_1.Consent;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ActivateUserTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a confirmed marketing consent payload for the user
        Consent consent = new Consent();
        consent.setConsent_id(UUID.randomUUID().toString());
        consent.setUser_id("user-123");
        consent.setRequested_at("2025-01-01T00:00:00Z");
        consent.setStatus("active");
        consent.setType("marketing");

        DataPayload consentPayload = new DataPayload();
        consentPayload.setData(objectMapper.valueToTree(consent));

        when(entityService.getItemsByCondition(
                eq(Consent.ENTITY_NAME),
                eq(Consent.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(consentPayload)));

        // Stub addItem for Audit persistence to succeed
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor instance (real)
        ActivateUser processor = new ActivateUser(serializerFactory, entityService, objectMapper);

        // Build a valid User payload that passes isValid()
        User user = new User();
        user.setUserId("user-123");
        user.setEmail("user@example.com");
        user.setEmailVerified(Boolean.TRUE);

        JsonNode userJson = objectMapper.valueToTree(user);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ActivateUser");
        DataPayload payload = new DataPayload();
        payload.setData(userJson);
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

        // Inspect returned entity JSON for expected sunny-day changes
        assertNotNull(response.getPayload());
        JsonNode returned = response.getPayload().getData();
        assertNotNull(returned);
        // gdprState should be set to "active"
        assertTrue(returned.has("gdprState"));
        assertEquals("active", returned.get("gdprState").asText());
        // marketingEnabled should be true
        assertTrue(returned.has("marketingEnabled"));
        assertTrue(returned.get("marketingEnabled").asBoolean());
        // auditRefs should contain at least one entry (audit id)
        assertTrue(returned.has("auditRefs"));
        JsonNode auditRefs = returned.get("auditRefs");
        assertTrue(auditRefs.isArray());
        assertTrue(auditRefs.size() >= 1);

        // Verify EntityService was used to fetch consents and to persist audit
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Consent.ENTITY_NAME), eq(Consent.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).addItem(eq("Audit"), eq(1), any());
    }
}