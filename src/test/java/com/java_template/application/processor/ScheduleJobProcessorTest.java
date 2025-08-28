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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ScheduleJobProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        ScheduleJobProcessor processor = new ScheduleJobProcessor(serializerFactory);

        // Create a valid Job entity. Use a sourceUrl that will likely be unreachable in unit test
        // but the entity itself must be valid so validation passes. The processor will mark the job
        // as FAILED when it cannot reach the source which is the expected behavior verified here.
        Job job = new Job();
        job.setJobId("daily-job-1");
        job.setScheduledAt(Instant.now().toString());
        job.setSourceUrl("http://localhost:9"); // very likely unreachable in test environment
        job.setStatus("SCHEDULED");
        job.setSummary(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(job);
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ScheduleJobProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - minimal happy-path checks
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should complete successfully");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data should be present");

        // Deserialize payload back to Job and inspect core state change
        Job processed = objectMapper.treeToValue(responseData, Job.class);
        assertNotNull(processed, "Processed Job should deserialize correctly");

        // Processor expected behavior when source unreachable: mark FAILED and set finishedAt and summary
        assertEquals("FAILED", processed.getStatus(), "Job should be marked FAILED when source is unreachable");
        assertNotNull(processed.getFinishedAt(), "finishedAt should be set when marking FAILED");
        assertNotNull(processed.getSummary(), "summary should be set when marking FAILED");
        assertTrue(processed.getSummary().toLowerCase().contains("source") || processed.getSummary().toLowerCase().contains("failed"),
                "summary should indicate source unreachable or failure");
    }
}