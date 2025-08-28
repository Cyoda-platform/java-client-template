package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RejectAdoptionProcessorTest {

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

        RejectAdoptionProcessor processor = new RejectAdoptionProcessor(serializerFactory);

        // Build a valid AdoptionRequest (must satisfy isValid())
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setId("adopt-1");
        adoptionRequest.setPetId("pet-123");
        adoptionRequest.setRequesterName("Jane Doe");
        adoptionRequest.setContactEmail("jane.doe@example.com");
        adoptionRequest.setContactPhone("555-0001");
        adoptionRequest.setMotivation("We have a loving home");
        adoptionRequest.setNotes("Initial submission");
        adoptionRequest.setProcessedBy(null); // processor should set this to manual_rejector
        adoptionRequest.setStatus("CREATED");
        adoptionRequest.setSubmittedAt(Instant.now().toString());

        JsonNode entityJson = objectMapper.valueToTree(adoptionRequest);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId(adoptionRequest.getId());
        request.setProcessorName("RejectAdoptionProcessor");
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
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Processor should set status to REJECTED
        assertEquals("REJECTED", out.get("status").asText());
        // processedBy should be set (manual_rejector in sunny path)
        assertNotNull(out.get("processedBy"));
        assertFalse(out.get("processedBy").asText().isBlank());
        // notes should contain the rejection note
        assertNotNull(out.get("notes"));
        String notesTxt = out.get("notes").asText();
        assertTrue(notesTxt.contains("Request rejected at"));
        // ensure original note was preserved (initial submission)
        assertTrue(notesTxt.contains("Initial submission"));
    }
}