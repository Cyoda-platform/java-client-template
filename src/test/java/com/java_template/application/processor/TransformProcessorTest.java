package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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

public class TransformProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked per instructions
        EntityService entityService = mock(EntityService.class);
        // Default stubs in case processor attempts to persist (HTTP will likely fail in test, so these may not be invoked)
        when(entityService.addItems(anyString(), anyInt(), anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(java.util.List.of()));
        when(entityService.addItems(anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(java.util.List.of()));
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(java.util.UUID.randomUUID()));

        // Construct processor (no Spring)
        TransformProcessor processor = new TransformProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid IngestionJob entity JSON that passes isValid()
        IngestionJob job = new IngestionJob();
        job.setJobId("job-123");
        // Provide a non-blank sourceUrl; HTTP requests are expected to fail in test environment and be caught by processor
        job.setSourceUrl("http://invalid.local");
        job.setStatus("PENDING");

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("TransformProcessor");
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

        // Assert - response exists and indicates success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload for updated job metadata (processor sets lastRunAt and status to COMPLETED or FAILED)
        assertNotNull(response.getPayload());
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData);

        // jobId should be preserved
        assertTrue(responseData.has("jobId"));
        assertEquals("job-123", responseData.get("jobId").asText());

        // lastRunAt should be set by processor (non-blank)
        assertTrue(responseData.has("lastRunAt"));
        assertFalse(responseData.get("lastRunAt").asText().isBlank());

        // Because HTTP endpoints are not available in the test, processor will mark status as "FAILED" in its happy path handling of that condition
        assertTrue(responseData.has("status"));
        assertEquals("FAILED", responseData.get("status").asText());

        // Verify that no persistence calls were made (since no derived entities were parsed/persisted)
        verify(entityService, never()).addItems(anyString(), anyInt(), anyCollection());
    }
}