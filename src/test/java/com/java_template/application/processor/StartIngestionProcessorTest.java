package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
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

public class StartIngestionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService as required
        EntityService entityService = mock(EntityService.class);
        // Stub addItems to return a single UUID to simulate persisted laureates
        when(entityService.addItems(anyString(), anyInt(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

        StartIngestionProcessor processor = new StartIngestionProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid Job payload (id, schedule, state are required by isValid)
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", "job-1");
        data.put("schedule", "*/5 * * * *");
        data.put("state", "PENDING");

        DataPayload payload = new DataPayload();
        payload.setData((JsonNode) data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("StartIngestionProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Processor sunny path should result in final state SUCCEEDED
        assertEquals("SUCCEEDED", out.get("state").asText());
        // Ensure counts were populated (non-null)
        assertTrue(out.has("recordsFetchedCount"));
        assertTrue(out.has("recordsProcessedCount"));
        assertTrue(out.has("recordsFailedCount"));

        // Verify entityService.addItems was called at least once to persist laureates (if any)
        verify(entityService, atLeastOnce()).addItems(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), anyList());
    }
}