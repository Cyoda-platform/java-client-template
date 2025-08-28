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

public class CancelRequestProcessorTest {

    @Test
    void sunnyDay_cancel_submitted_request_is_cancelled() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        CancelRequestProcessor processor = new CancelRequestProcessor(serializerFactory);

        // Build a valid AdoptionRequest that passes isValid() and is in a cancellable state
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId("req-123");
        adoptionRequest.setPetId("pet-1");
        adoptionRequest.setRequesterId("owner-1");
        adoptionRequest.setStatus("submitted");
        adoptionRequest.setSubmittedAt(Instant.now().toString());
        // notes and decisionAt left null to allow processor to set them

        JsonNode entityJson = objectMapper.valueToTree(adoptionRequest);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CancelRequestProcessor");
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
        assertTrue(response.getSuccess(), "Processor should report success for sunny-day cancellation");

        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Verify core sunny-day changes
        assertEquals("cancelled", out.get("status").asText(), "Status should be updated to 'cancelled'");
        assertTrue(out.hasNonNull("decisionAt") && !out.get("decisionAt").asText().isBlank(), "decisionAt should be set");
        assertTrue(out.hasNonNull("notes") && out.get("notes").asText().contains("Cancelled by requester"), "notes should contain cancellation note");
    }
}