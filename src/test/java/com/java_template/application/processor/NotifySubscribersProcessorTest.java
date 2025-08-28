package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotifySubscribersProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Create a subscriber that is active and matches state=SUCCEEDED (no webhook so email path is used)
        Subscriber subscriber = new Subscriber();
        subscriber.setId("sub-1");
        subscriber.setActive(true);
        subscriber.setCreatedAt("2025-01-01T00:00:00Z");
        subscriber.setEmail("notify@example.com");
        subscriber.setName("Notify Me");
        subscriber.setFilters("state=SUCCEEDED"); // will match the job state
        // no webhookUrl set to force email (simulated) code path

        DataPayload subscriberPayload = new DataPayload();
        subscriberPayload.setData(objectMapper.valueToTree(subscriber));

        when(entityService.getItems(eq(Subscriber.ENTITY_NAME), eq(Subscriber.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(subscriberPayload)));

        // Build processor under test
        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job entity that represents a succeeded job (sunny path)
        Job job = new Job();
        job.setId("job-1");
        job.setSourceUrl("http://example.com/source");
        job.setSchedule("0 0 * * *");
        job.setState("SUCCEEDED"); // must be SUCCEEDED to trigger notifications
        job.setProcessedCount(10);
        job.setFailedCount(0);

        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(job));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("NotifySubscribersProcessor");
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

        // Inspect returned payload for expected state change and summary
        DataPayload respPayload = response.getPayload();
        assertNotNull(respPayload);
        Job outJob = objectMapper.treeToValue(respPayload.getData(), Job.class);

        assertNotNull(outJob);
        assertEquals("NOTIFIED_SUBSCRIBERS", outJob.getState(), "Job should be marked as NOTIFIED_SUBSCRIBERS after notifications");
        assertNotNull(outJob.getFinishedAt(), "finishedAt should be set");
        assertNotNull(outJob.getErrorSummary(), "errorSummary should be set (summary may indicate successes/failures)");
        assertTrue(outJob.getErrorSummary().contains("notificationsAttempted"), "errorSummary should contain notification summary");
    }
}