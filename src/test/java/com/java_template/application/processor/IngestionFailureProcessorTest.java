package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
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

public class IngestionFailureProcessorTest {

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

        IngestionFailureProcessor processor = new IngestionFailureProcessor(serializerFactory);

        // Build a valid Job entity that passes isValid()
        Job job = new Job();
        job.setId("job-1");
        job.setSourceUrl("http://example.com/data");
        job.setSchedule("0 0 * * *");
        job.setState("SCHEDULED");
        job.setProcessedCount(10);
        job.setFailedCount(0);
        job.setErrorSummary(null); // processor should populate this

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("IngestionFailureProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

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

        JsonNode outNode = response.getPayload().getData();
        assertNotNull(outNode);

        // Map back to Job for convenient assertions
        Job outJob = objectMapper.treeToValue(outNode, Job.class);
        assertNotNull(outJob);

        // Core sunny-path assertions
        assertEquals("FAILED", outJob.getState(), "Job state should be set to FAILED");
        assertNotNull(outJob.getFinishedAt(), "finishedAt should be populated");
        assertFalse(outJob.getFinishedAt().isBlank(), "finishedAt should be a non-blank string");
        assertEquals(Integer.valueOf(job.getFailedCount() + 1), outJob.getFailedCount(), "failedCount should be incremented by 1");
        assertNotNull(outJob.getProcessedCount(), "processedCount should remain non-null");
        assertNotNull(outJob.getErrorSummary(), "errorSummary should be set");
        assertTrue(outJob.getErrorSummary().startsWith("Ingestion failed at"), "errorSummary should indicate ingestion failure");
    }
}