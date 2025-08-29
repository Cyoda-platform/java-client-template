package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PersistProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);
        // Stub persistence calls that may happen in the sunny path
        when(entityService.addItems(anyString(), anyInt(), anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor instance (no Spring)
        PersistProcessor processor = new PersistProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid IngestionJob entity that passes isValid()
        IngestionJob job = new IngestionJob();
        job.setJobId("job-123");
        job.setSourceUrl("http://localhost"); // calls will be attempted but failures are caught by processor
        job.setStatus("PENDING");
        job.setDataFormats("JSON");

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PersistProcessor");
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

        // Assert - basic sunny-day expectations
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode dataNode = response.getPayload().getData();
        assertNotNull(dataNode);
        // Verify that processor updated status to COMPLETED in the happy path
        assertEquals("COMPLETED", dataNode.get("status").asText());
        // lastRunAt should have been set
        assertTrue(dataNode.hasNonNull("lastRunAt"));
    }
}