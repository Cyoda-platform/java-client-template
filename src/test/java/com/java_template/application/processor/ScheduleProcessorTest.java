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

public class ScheduleProcessorTest {

    @Test
    void sunnyDay_scheduleProcessor_transitions_scheduled_to_ingesting() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is allowed to be mocked per instructions
        EntityService entityService = mock(EntityService.class);

        ScheduleProcessor processor = new ScheduleProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid Job entity that is in SCHEDULED state
        Job job = new Job();
        job.setId("job-" + UUID.randomUUID());
        job.setSourceUrl("https://example.com/source");
        job.setSchedule("0 0 * * *");
        job.setState("SCHEDULED");
        // isValid requires non-null counts >= 0
        job.setProcessedCount(0);
        job.setFailedCount(0);
        job.setErrorSummary(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);

        JsonNode jobJson = objectMapper.valueToTree(job);

        DataPayload payload = new DataPayload();
        payload.setData(jobJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("ScheduleProcessor");
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
        assertTrue(response.getSuccess(), "Processor should succeed on sunny path");
        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Response payload data should not be null");

        Job outJob = objectMapper.treeToValue(out, Job.class);
        // state should have been transitioned to INGESTING
        assertEquals("INGESTING", outJob.getState(), "Job should be transitioned to INGESTING");
        // startedAt should be set
        assertNotNull(outJob.getStartedAt(), "startedAt should be populated");
        assertFalse(outJob.getStartedAt().isBlank(), "startedAt should not be blank");
        // counts should be present and non-negative (we initialized to 0)
        assertNotNull(outJob.getProcessedCount());
        assertNotNull(outJob.getFailedCount());
        assertEquals(0, outJob.getProcessedCount().intValue());
        assertEquals(0, outJob.getFailedCount().intValue());
        // errorSummary should be cleared (null)
        assertNull(outJob.getErrorSummary(), "errorSummary should be null after transition");
    }
}