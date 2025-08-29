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

public class PublishMediaTest {

    @Test
    void sunnyDay_publishMedia_setsStatusPublished_and_appendsAudit() throws Exception {
        // Setup real Jackson ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore unknown properties as required
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor instance
        PublishMedia processor = new PublishMedia(serializerFactory, entityService, objectMapper);

        // Build a valid Media entity that is not yet published (sunny path)
        Media media = new Media();
        media.setMedia_id("media-123");
        media.setOwner_id("owner-456");
        media.setFilename("file.jpg");
        media.setMime("image/jpeg");
        media.setCreated_at("2025-01-01T00:00:00Z");
        media.setStatus("processing"); // not "published" so processor will publish it

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(media);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PublishMedia");
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

        // Assert basic response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");
        assertNotNull(response.getPayload(), "Response payload should not be null");
        assertNotNull(response.getPayload().getData(), "Response payload data should not be null");

        // Deserialize returned entity and assert status changed to published
        JsonNode returnedData = response.getPayload().getData();
        Media returnedMedia = objectMapper.treeToValue(returnedData, Media.class);
        assertEquals("media-123", returnedMedia.getMedia_id(), "Media id should be preserved");
        assertEquals("published", returnedMedia.getStatus(), "Media status should be set to published");

        // Verify that an audit was attempted to be created
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}