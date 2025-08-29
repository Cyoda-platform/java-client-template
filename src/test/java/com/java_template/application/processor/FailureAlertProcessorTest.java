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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FailureAlertProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and SerializerFactory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only mock EntityService as required by FailureAlertProcessor constructor
        EntityService entityService = mock(EntityService.class);
        // Stub addItem to simulate creation of WeeklyReport and return a UUID
        when(entityService.addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor instance (no Spring)
        FailureAlertProcessor processor = new FailureAlertProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid IngestionJob entity JSON that passes isValid() and has notifyEmail to trigger WeeklyReport creation
        IngestionJob job = new IngestionJob();
        job.setJobId("job-123");
        job.setSourceUrl("http://example.com/source");
        job.setStatus("RUNNING");
        job.setNotifyEmail("ops@example.com");

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FailureAlertProcessor");
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

        // Assert - basic sunny-day assertions
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData);

        // Processor should set status to FAILED
        assertTrue(respData.has("status"));
        assertEquals("FAILED", respData.get("status").asText());

        // Processor should set/update lastRunAt to a non-blank string
        assertTrue(respData.has("lastRunAt"));
        String lastRunAt = respData.get("lastRunAt").asText();
        assertNotNull(lastRunAt);
        assertFalse(lastRunAt.isBlank());

        // Verify that a WeeklyReport was attempted to be created
        verify(entityService, atLeastOnce()).addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any());
    }
}