package com.java_template.application.processor;

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

import static org.junit.jupiter.api.Assertions.*;

public class RejectionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        RejectionProcessor processor = new RejectionProcessor(serializerFactory);

        // Build a valid AdoptionRequest entity that passes isValid()
        AdoptionRequest inputEntity = new AdoptionRequest();
        inputEntity.setRequestId("req-123");
        inputEntity.setPetId("pet-1");
        inputEntity.setRequesterId("owner-1");
        inputEntity.setStatus("submitted"); // non-terminal status so processor will change it
        inputEntity.setSubmittedAt("2025-01-01T00:00:00Z");
        // notes and decisionAt left null to exercise setting of default rejection note and timestamp

        // Build request with payload
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(inputEntity));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(inputEntity.getRequestId());
        request.setProcessorName("RejectionProcessor");
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Deserialize returned payload to AdoptionRequest and verify sunny-day changes
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        AdoptionRequest outEntity = objectMapper.treeToValue(response.getPayload().getData(), AdoptionRequest.class);
        assertNotNull(outEntity);
        assertEquals(inputEntity.getRequestId(), outEntity.getRequestId(), "requestId should be preserved");
        assertEquals("rejected", outEntity.getStatus(), "status should be set to rejected");
        assertNotNull(outEntity.getDecisionAt(), "decisionAt should be set");
        assertFalse(outEntity.getDecisionAt().isBlank(), "decisionAt should be non-blank");
        assertNotNull(outEntity.getNotes(), "notes should be set");
        assertFalse(outEntity.getNotes().isBlank(), "notes should be non-blank");
        assertEquals("Request rejected.", outEntity.getNotes(), "notes should contain default rejection note when none provided");
    }
}