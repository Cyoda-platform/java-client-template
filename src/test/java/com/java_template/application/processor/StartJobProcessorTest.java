package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StartJobProcessorTest {

    @Test
    void sunnyDay_startJob_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        StartJobProcessor processor = new StartJobProcessor(serializerFactory);

        // Build a minimal PetIngestionJob that satisfies StartJobProcessor.isValidEntity (status PENDING, jobName and sourceUrl present)
        PetIngestionJob job = new PetIngestionJob();
        job.setJobName("ingest-job-1");
        job.setSourceUrl("http://example.com/pets.csv");
        job.setStatus("PENDING");
        // startedAt and processedCount intentionally left null to exercise initialization logic

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("StartJobProcessor");
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

        // Assert - basic sunny-day expectations
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Output payload data should not be null");
        // Processor should transition status to VALIDATING
        assertEquals("VALIDATING", out.get("status").asText(), "Status should be transitioned to VALIDATING");
        // startedAt should be set by the processor
        assertTrue(out.hasNonNull("startedAt"), "startedAt should be set");
        assertFalse(out.get("startedAt").asText().isBlank(), "startedAt should be a non-blank string");
        // processedCount should be initialized to 0
        assertTrue(out.has("processedCount"), "processedCount should be present");
        assertEquals(0, out.get("processedCount").asInt(), "processedCount should be initialized to 0");
        // errors list should exist (may be empty)
        assertTrue(out.has("errors") && out.get("errors").isArray(), "errors array should be present");
    }
}