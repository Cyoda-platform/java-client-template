package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.consent.version_1.Consent;
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

public class CreateVerificationTokenTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - configure real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any(Audit.class)))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor with real serializers and mocked EntityService
        CreateVerificationToken processor = new CreateVerificationToken(serializerFactory, entityService, objectMapper);

        // Prepare a valid Consent entity that passes isValid()
        Consent consent = new Consent();
        consent.setConsent_id("consent-1");
        consent.setUser_id("user-1");
        consent.setRequested_at("2025-01-01T00:00:00Z");
        consent.setStatus("requested");
        consent.setType("email_verification");

        JsonNode entityJson = objectMapper.valueToTree(consent);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CreateVerificationToken");
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
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should succeed in sunny path");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData, "Payload data should be present");

        // The processor should set evidence_ref and change status to pending_verification
        assertEquals("pending_verification", outData.get("status").asText(), "Status should be updated to pending_verification");
        assertNotNull(outData.get("evidence_ref"), "evidence_ref should be set");
        assertFalse(outData.get("evidence_ref").asText().isBlank(), "evidence_ref should be non-empty");

        // Verify EntityService.addItem was invoked to persist an Audit record
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any(Audit.class));
    }
}