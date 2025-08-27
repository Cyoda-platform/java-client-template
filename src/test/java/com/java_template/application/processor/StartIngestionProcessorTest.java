package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StartIngestionProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        EntityService entityService = mock(EntityService.class);
        // stub addItem to complete immediately
        when(entityService.addItem(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        StartIngestionProcessor processor = new StartIngestionProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job payload (must satisfy Job.isValid())
        JsonNode jobNode = objectMapper.createObjectNode()
                .put("jobId", "j1")
                .put("state", "READY")
                .put("retryCount", 0)
                // Use a public JSON endpoint that returns an array so processor will iterate records.
                // This must be a reachable endpoint in the test environment; jsonplaceholder returns an array.
                .put("sourceEndpoint", "https://jsonplaceholder.typicode.com/posts");

        DataPayload payload = new DataPayload();
        payload.setData(jobNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("StartIngestionProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Job should have been finalized: finishedAt set and state succeeded in sunny-day
        assertTrue(out.has("state"));
        assertEquals("SUCCEEDED", out.get("state").asText());
        assertTrue(out.has("finishedAt"));
        assertTrue(out.get("finishedAt").isTextual());
        assertTrue(out.has("resultSummary"));
        assertTrue(out.get("resultSummary").asText().startsWith("Processed="));

        // Verify entityService was used to add Laureate items
        verify(entityService, atLeastOnce()).addItem(eq(Laureate.ENTITY_NAME), anyString(), any());
    }
}