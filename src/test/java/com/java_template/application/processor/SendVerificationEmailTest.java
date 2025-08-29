package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.consent.version_1.Consent;
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

public class SendVerificationEmailTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a User that will be returned by entityService.getItemsByCondition
        User user = new User();
        user.setUserId("u1");
        user.setEmail("user@example.com");
        JsonNode userJson = objectMapper.valueToTree(user);
        DataPayload userPayload = new DataPayload();
        userPayload.setData(userJson);

        when(entityService.getItemsByCondition(
                eq(User.ENTITY_NAME),
                eq(User.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(userPayload)));

        // Stub audit add to succeed
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor with real serializers and mocked EntityService
        SendVerificationEmail processor = new SendVerificationEmail(serializerFactory, entityService, objectMapper);

        // Build a valid Consent entity JSON payload that passes isValid()
        Consent consent = new Consent();
        consent.setConsent_id("c1");
        consent.setUser_id("u1"); // matches the mocked user
        consent.setRequested_at("2025-01-01T00:00:00Z");
        consent.setStatus("new");
        consent.setType("email_verification");
        JsonNode consentJson = objectMapper.valueToTree(consent);

        DataPayload payload = new DataPayload();
        payload.setData(consentJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("SendVerificationEmail");
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
        assertTrue(response.getSuccess(), "Processor should report success");

        // Inspect returned payload data for evidence_ref being set by processor (sunny path)
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode returned = response.getPayload().getData();
        assertNotNull(returned, "Returned data should not be null");
        assertTrue(returned.hasNonNull("evidence_ref"), "Evidence ref should be present");
        String evidenceRef = returned.get("evidence_ref").asText();
        assertNotNull(evidenceRef);
        assertFalse(evidenceRef.isBlank(), "evidence_ref should be non-blank");

        // Optionally ensure audit persistence was invoked
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}