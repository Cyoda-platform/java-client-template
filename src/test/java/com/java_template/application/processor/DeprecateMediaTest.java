package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DeprecateMediaTest {

    @Test
    void sunnyDay_deprecate_media() throws Exception {
        // Arrange - configure real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService may be mocked
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        DeprecateMedia processor = new DeprecateMedia(serializerFactory, entityService, objectMapper);

        // Build a valid Media entity that passes isValid()
        Media media = new Media();
        media.setMedia_id("media-1");
        media.setFilename("image.png");
        media.setMime("image/png");
        media.setCreated_at("2025-01-01T00:00:00Z");
        media.setStatus("active");
        media.setOwner_id("owner-1");

        JsonNode entityJson = objectMapper.valueToTree(media);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DeprecateMedia");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic sunny-day assertions
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor should succeed in sunny-day path");

        // Inspect payload to ensure media status was set to "deprecated"
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData, "Response payload data should not be null");
        assertEquals("deprecated", outData.get("status").asText(), "Media status should be 'deprecated' after processing");

        // Verify that an audit record was attempted to be persisted
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}