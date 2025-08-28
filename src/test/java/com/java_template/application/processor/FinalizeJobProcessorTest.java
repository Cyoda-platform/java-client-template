package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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

public class FinalizeJobProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        FinalizeJobProcessor processor = new FinalizeJobProcessor(serializerFactory);

        // Build a valid PetIngestionJob that represents a completed sunny-day scenario:
        // - status present (e.g., "RUNNING")
        // - processedCount > 0
        // - errors list empty
        // - jobName, sourceUrl, startedAt present
        PetIngestionJob job = new PetIngestionJob();
        job.setJobName("import-1");
        job.setSourceUrl("http://example.com/pets.csv");
        job.setStartedAt("2025-01-01T00:00:00Z");
        job.setStatus("RUNNING"); // valid initial status
        job.setProcessedCount(5);
        // errors is initialized to empty list by default

        JsonNode payloadNode = objectMapper.valueToTree(job);
        DataPayload payload = new DataPayload();
        payload.setData(payloadNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FinalizeJobProcessor");
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
        assertTrue(response.getSuccess(), "Response should indicate success");

        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Response payload data should be present");
        assertEquals("COMPLETED", out.get("status").asText(), "Job should be marked COMPLETED in sunny path");
        assertTrue(out.hasNonNull("completedAt") && !out.get("completedAt").asText().isBlank(),
                "completedAt should be set by the processor");
    }
}