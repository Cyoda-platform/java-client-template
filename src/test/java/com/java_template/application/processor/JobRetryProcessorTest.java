package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class JobRetryProcessorTest {

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

        // EntityService is required by constructor; mock it per rules (no other mocking)
        EntityService entityService = mock(EntityService.class);

        JobRetryProcessor processor = new JobRetryProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job entity that triggers the retry logic (state = "FAILED")
        Job job = new Job();
        job.setId("job-1");
        job.setSourceUrl("http://example.com");
        job.setSchedule("0 0 * * *");
        job.setState("FAILED");
        job.setProcessedCount(0);
        job.setFailedCount(1);
        job.setErrorSummary("SomeFailureMessage");
        job.setStartedAt(null);
        job.setFinishedAt(null);

        JsonNode entityJson = objectMapper.valueToTree(job);

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r-" + UUID.randomUUID());
        request.setRequestId("req-" + UUID.randomUUID());
        request.setEntityId(job.getId());
        request.setProcessorName("JobRetryProcessor");
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
        assertNotNull(response.getPayload().getData());

        // Convert returned payload to Job and assert retry updates
        Job outJob = objectMapper.treeToValue(response.getPayload().getData(), Job.class);
        assertNotNull(outJob);

        // Sunny-day expectations: state moved to SCHEDULED and errorSummary updated with retryAttempt=1 prefix
        assertEquals("SCHEDULED", outJob.getState());
        assertNotNull(outJob.getErrorSummary());
        assertTrue(outJob.getErrorSummary().startsWith("retryAttempt=1"));
        // startedAt and finishedAt should be cleared (null) as part of retry scheduling
        assertNull(outJob.getStartedAt());
        assertNull(outJob.getFinishedAt());
    }
}