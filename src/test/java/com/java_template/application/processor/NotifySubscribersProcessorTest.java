package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare one active subscriber (uses "email" preference to avoid HTTP webhook calls)
        Subscriber sub = new Subscriber();
        sub.setSubscriberId("sub-1");
        sub.setName("Test Subscriber");
        sub.setContactEmail("test@example.com");
        sub.setDeliveryPreference("email"); // email avoids webhook HttpClient.send
        sub.setActive(true);

        DataPayload subPayload = new DataPayload();
        subPayload.setData(objectMapper.valueToTree(sub));

        when(entityService.getItemsByCondition(
                anyString(),
                anyInt(),
                any(),
                anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(List.of(subPayload)));

        // Create processor (real)
        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job entity JSON that passes Job.isValid()
        Job job = new Job();
        job.setJobId("job-1");
        job.setScheduledAt("2025-01-01T00:00:00Z");
        job.setSourceUrl("http://example.com/source");
        job.setStatus("PENDING");
        job.setSummary(null);

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifySubscribersProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - minimal sunny-day expectations
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");
        assertNotNull(response.getPayload(), "Response payload should not be null");
        assertNotNull(response.getPayload().getData(), "Response payload data should not be null");

        // Convert returned data to Job and verify state changes
        Job resultJob = objectMapper.treeToValue(response.getPayload().getData(), Job.class);
        assertNotNull(resultJob, "Resulting Job should be deserializable");
        assertEquals("NOTIFIED_SUBSCRIBERS", resultJob.getStatus(), "Job status should be updated to NOTIFIED_SUBSCRIBERS");
        assertNotNull(resultJob.getFinishedAt(), "finishedAt should be set");
        assertFalse(resultJob.getFinishedAt().isBlank(), "finishedAt should not be blank");
        assertTrue(resultJob.getSummary() != null && resultJob.getSummary().contains("notified"),
                "Summary should indicate notified subscribers");
    }
}