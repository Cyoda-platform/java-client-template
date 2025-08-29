package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.media.version_1.Media;
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

public class ProcessMediaTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor directly (no Spring)
        ProcessMedia processor = new ProcessMedia(serializerFactory, entityService, objectMapper);

        // Build a valid Media entity that passes isValid()
        Media media = new Media();
        media.setMedia_id(UUID.randomUUID().toString());
        media.setOwner_id(UUID.randomUUID().toString());
        media.setFilename("image.jpg");
        media.setMime("image/jpeg");
        media.setCreated_at("2025-01-01T00:00:00Z");
        media.setStatus("uploaded"); // initial status before processing

        JsonNode entityJson = objectMapper.valueToTree(media);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ProcessMedia");
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
        assertTrue(response.getSuccess(), "Processing should succeed");

        // Inspect returned payload data for expected sunny-day changes
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode returned = response.getPayload().getData();
        assertNotNull(returned, "Returned data should not be null");

        // status should have been updated to "processed"
        assertTrue(returned.has("status"));
        assertEquals("processed", returned.get("status").asText());

        // cdn_ref should have been set and follow the expected pattern
        assertTrue(returned.has("cdn_ref"));
        assertTrue(returned.get("cdn_ref").asText().startsWith("cdn://media/"));

        // versions array should exist and contain at least two derived entries
        assertTrue(returned.has("versions"));
        JsonNode versionsNode = returned.get("versions");
        assertTrue(versionsNode.isArray());
        assertTrue(versionsNode.size() >= 2, "Expected at least 2 derived versions");

        // Verify EntityService.addItem was invoked to persist the Audit
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}