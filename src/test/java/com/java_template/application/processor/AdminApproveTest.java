package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.post.version_1.Post;
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

public class AdminApproveTest {

    @Test
    void sunnyDay_adminApprove_publishNow() throws Exception {
        // Setup real ObjectMapper and serializers (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);
        // Stub addItem used to persist Audit records
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor directly (no Spring)
        AdminApprove processor = new AdminApprove(serializerFactory, entityService, objectMapper);

        // Build a minimal valid Post entity JSON that is in "in_review" and has no publish_datetime
        Post postEntity = new Post();
        postEntity.setId("post-1");
        postEntity.setOwner_id("owner-1");
        postEntity.setTitle("A Title");
        postEntity.setSlug("a-title");
        postEntity.setStatus("in_review");
        // ensure cache_control is null so processor sets default
        postEntity.setCache_control(null);
        // No publish_datetime -> should publish immediately

        JsonNode entityJson = objectMapper.valueToTree(postEntity);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("post-1");
        request.setProcessorName("AdminApprove");
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
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload data for sunny-day state changes
        assertNotNull(response.getPayload());
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData);

        // Status should be updated to "published"
        assertEquals("published", outData.path("status").asText());

        // published_at should be set (non-empty)
        String publishedAt = outData.path("published_at").asText();
        assertNotNull(publishedAt);
        assertFalse(publishedAt.isBlank());

        // cache_control should be set to default value by processor when missing
        assertEquals("public, max-age=3600", outData.path("cache_control").asText());

        // Verify that an audit record was attempted to be added
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}