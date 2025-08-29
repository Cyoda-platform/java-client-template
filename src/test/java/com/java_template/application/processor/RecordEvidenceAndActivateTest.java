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

public class RecordEvidenceAndActivateTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor directly (no Spring)
        RecordEvidenceAndActivate processor = new RecordEvidenceAndActivate(serializerFactory, entityService, objectMapper);

        // Build a valid Consent entity that will pass isValid()
        Consent consent = new Consent();
        consent.setConsent_id("consent-1");
        consent.setUser_id("user-1");
        consent.setRequested_at("2025-01-01T00:00:00Z");
        consent.setStatus("pending");
        consent.setType("email_confirmation");
        // evidence_ref and granted_at intentionally left null to be set by processor

        JsonNode entityJson = objectMapper.valueToTree(consent);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("RecordEvidenceAndActivate");
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
        assertTrue(response.getSuccess(), "Processing should succeed in sunny path");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode dataNode = response.getPayload().getData();
        assertNotNull(dataNode, "Payload data should not be null");

        // Verify processor set evidence_ref, status and granted_at
        assertTrue(dataNode.hasNonNull("evidence_ref"), "evidence_ref should be set");
        assertFalse(dataNode.get("evidence_ref").asText().isBlank(), "evidence_ref should not be blank");

        assertTrue(dataNode.hasNonNull("status"), "status should be present");
        assertEquals("active", dataNode.get("status").asText(), "status should be set to active");

        assertTrue(dataNode.hasNonNull("granted_at"), "granted_at should be set");
        assertFalse(dataNode.get("granted_at").asText().isBlank(), "granted_at should not be blank");

        // Verify that an audit persistence attempt was made
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}