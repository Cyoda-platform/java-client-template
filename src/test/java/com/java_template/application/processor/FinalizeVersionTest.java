package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.postversion.version_1.PostVersion;
import com.java_template.application.entity.audit.version_1.Audit;
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

public class FinalizeVersionTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange: real ObjectMapper configured to ignore unknown properties
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);
        // Stub addItem for Audit persistence to return a completed UUID
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor (no Spring)
        FinalizeVersion processor = new FinalizeVersion(serializerFactory, entityService, objectMapper);

        // Create a minimal valid PostVersion entity that passes isValid()
        PostVersion postVersion = new PostVersion();
        postVersion.setVersion_id("v-123");
        postVersion.setPost_id("p-456");
        postVersion.setCreated_at("2025-01-01T00:00:00Z");
        // Provide rich content to trigger normalization and chunk generation
        postVersion.setContent_rich("<p>Hello <strong>world</strong>! This is a test content to be normalized.</p>");
        // Leave normalized_text, chunks_meta, embeddings_ref null to let processor populate them

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(postVersion);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FinalizeVersion");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
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

        // Assert basic response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor should report success in sunny-day path");

        // Inspect returned payload data for expected sunny-day modifications
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned data must be present");

        // normalized_text should be created from content_rich
        JsonNode normalizedNode = returnedData.get("normalized_text");
        assertNotNull(normalizedNode, "normalized_text should be set by processor");
        assertFalse(normalizedNode.asText().isBlank(), "normalized_text should not be blank");

        // embeddings_ref should be set and non-blank
        JsonNode embeddingsRefNode = returnedData.get("embeddings_ref");
        assertNotNull(embeddingsRefNode, "embeddings_ref should be set");
        assertFalse(embeddingsRefNode.asText().isBlank(), "embeddings_ref should not be blank");

        // chunks_meta should be present and non-empty
        JsonNode chunksMetaNode = returnedData.get("chunks_meta");
        assertNotNull(chunksMetaNode, "chunks_meta should be present");
        assertTrue(chunksMetaNode.isArray(), "chunks_meta should be an array");
        assertTrue(chunksMetaNode.size() > 0, "chunks_meta should contain at least one chunk");

        // Verify that the processor attempted to persist an Audit record
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}