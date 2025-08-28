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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class JobCompletionProcessorTest {

    @Test
    void sunnyDay_completeJob() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // EntityService is required by the processor constructor but not used in the sunny path here.
        EntityService entityService = mock(EntityService.class);

        JobCompletionProcessor processor = new JobCompletionProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job entity for the sunny-path
        Job job = new Job();
        job.setId("job-1");
        job.setSourceUrl("http://example.com/source");
        job.setSchedule("0 0 * * *");
        job.setState("INGESTING"); // processor only acts when INGESTING
        job.setProcessedCount(10);
        job.setFailedCount(0);
        job.setErrorSummary(""); // blank should be cleared when succeeded
        job.setStartedAt(null);
        job.setFinishedAt(null);

        JsonNode entityJson = objectMapper.valueToTree(job);

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("JobCompletionProcessor");
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
        JsonNode outNode = response.getPayload().getData();
        assertNotNull(outNode);

        // Convert back to Job to inspect fields easily
        Job resultJob = objectMapper.treeToValue(outNode, Job.class);
        assertNotNull(resultJob);

        // Sunny-day expectations: state changed to SUCCEEDED, finishedAt set, processedCount preserved, errorSummary cleared to null
        assertEquals("SUCCEEDED", resultJob.getState());
        assertNotNull(resultJob.getFinishedAt());
        assertEquals(10, resultJob.getProcessedCount());
        assertNull(resultJob.getErrorSummary());
    }
}