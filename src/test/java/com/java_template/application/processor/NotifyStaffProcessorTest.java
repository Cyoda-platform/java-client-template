package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class NotifyStaffProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // EntityService is required by constructor; mock it but processor does not call it in sunny path
        EntityService entityService = mock(EntityService.class);

        NotifyStaffProcessor processor = new NotifyStaffProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest entity that passes isValid()
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setId("adopt-1");
        adoptionRequest.setPetId("pet-123");
        adoptionRequest.setRequesterName("Jane Doe");
        adoptionRequest.setContactEmail("jane.doe@example.com");
        adoptionRequest.setContactPhone("555-0100");
        adoptionRequest.setMotivation("Loves animals");
        adoptionRequest.setNotes("Initial note");
        adoptionRequest.setProcessedBy(null);
        adoptionRequest.setStatus("CREATED");
        adoptionRequest.setSubmittedAt("2025-01-01T12:00:00Z");

        JsonNode entityJson = objectMapper.valueToTree(adoptionRequest);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("adopt-1");
        request.setProcessorName("NotifyStaffProcessor");
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
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "response should indicate success");
        assertNotNull(response.getPayload(), "response payload should not be null");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "response data should not be null");
        assertEquals("UNDER_REVIEW", out.get("status").asText(), "status should be set to UNDER_REVIEW");
        String notes = out.get("notes").asText();
        assertNotNull(notes);
        assertTrue(notes.contains("Initial note"), "original notes should be preserved");
        assertTrue(notes.contains("Staff notified at"), "notes should contain notification entry");
    }
}