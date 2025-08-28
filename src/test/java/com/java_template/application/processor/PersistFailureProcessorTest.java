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

public class PersistFailureProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        PersistFailureProcessor processor = new PersistFailureProcessor(serializerFactory);

        // Create a valid PetIngestionJob entity (must satisfy isValid())
        PetIngestionJob job = new PetIngestionJob();
        job.setJobName("job-123");
        job.setSourceUrl("https://example.com/source");
        job.setStartedAt("2025-01-01T00:00:00Z");
        job.setStatus("RUNNING"); // not COMPLETED, so completedAt may be null
        job.setProcessedCount(0);
        job.setErrors(new java.util.ArrayList<>());

        // Convert entity to JsonNode for payload
        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId("entity-1");
        request.setProcessorName("PersistFailureProcessor");
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

        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Check core sunny-day transformations applied by PersistFailureProcessor
        assertEquals("FAILED", out.get("status").asText(), "status should be set to FAILED");
        assertTrue(out.hasNonNull("completedAt"), "completedAt should be set");
        assertTrue(out.has("errors") && out.get("errors").isArray(), "errors should be an array");
        assertTrue(out.get("errors").size() >= 1, "errors should contain at least one entry");
        // Check that the processor name is mentioned in the appended error detail
        String firstError = out.get("errors").get(0).asText();
        assertTrue(firstError.contains("PersistFailureProcessor"), "error detail should mention the processor name");

        // processedCount should remain numeric (was 0)
        assertEquals(0, out.get("processedCount").asInt());
    }
}