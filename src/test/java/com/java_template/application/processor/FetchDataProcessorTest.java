package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FetchDataProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real Jackson serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService may be mocked
        EntityService entityService = mock(EntityService.class);

        // Stub persistence calls to succeed
        when(entityService.addItems(eq("Product"), anyInt(), anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));
        when(entityService.addItems(eq("SalesRecord"), anyInt(), anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));
        when(entityService.addItems(eq("InventorySnapshot"), anyInt(), anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));
        when(entityService.addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate the processor (no Spring)
        FetchDataProcessor processor = new FetchDataProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid IngestionJob payload.
        // Provide a sourceUrl that will not cause the test to wait on external services (use a localhost unused port).
        IngestionJob job = new IngestionJob();
        job.setJobId("job-1");
        job.setSourceUrl("http://127.0.0.1:1"); // likely to fail fast with connection refused
        job.setStatus("PENDING");
        // include MON in cron to deterministically trigger WeeklyReport branch without relying on day-of-week
        job.setScheduleCron("0 0 * * MON");

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FetchDataProcessor");
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

        // Assert basic response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor response should indicate success");

        // Inspect payload data for processor side effects: status and lastRunAt should be set/updated
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode dataNode = response.getPayload().getData();
        assertNotNull(dataNode, "Payload data should not be null");

        // Status should have been updated away from initial PENDING (either COMPLETED or FAILED)
        assertTrue(dataNode.has("status"), "Entity should contain status field after processing");
        String finalStatus = dataNode.get("status").asText();
        assertNotEquals("PENDING", finalStatus, "Processor should have updated the job status");

        // lastRunAt should be set by the processor
        assertTrue(dataNode.has("lastRunAt"), "Entity should contain lastRunAt field after processing");
        String lastRunAt = dataNode.get("lastRunAt").asText();
        assertNotNull(lastRunAt);
        assertFalse(lastRunAt.isBlank(), "lastRunAt should be a non-blank timestamp");

        // Verify that WeeklyReport enqueue was attempted due to scheduleCron containing MON
        verify(entityService, atLeastOnce()).addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any());
    }
}