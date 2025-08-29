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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SubmitForReviewTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only the EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor (no Spring)
        SubmitForReview processor = new SubmitForReview(serializerFactory, entityService, objectMapper);

        // Build a valid Post entity that will pass isValid() and processor-specific checks
        Post post = new Post();
        post.setId("post-1");
        post.setOwner_id("owner-1");
        post.setTitle("A title");
        post.setSlug("a-title");
        post.setStatus("draft");
        post.setCurrent_version_id("v1");
        // tags with duplicates and whitespace to verify normalization
        post.setTags(List.of(" Tag", "tag", "Other "));

        JsonNode postJson = objectMapper.valueToTree(post);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId("post-1");
        request.setProcessorName("SubmitForReview");
        DataPayload payload = new DataPayload();
        payload.setData(postJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic response checks
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload for expected sunny-day changes: status -> in_review and tags normalized
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);

        assertEquals("in_review", returnedData.get("status").asText());

        // Expect normalized tags: ["tag","other"]
        JsonNode tagsNode = returnedData.get("tags");
        assertNotNull(tagsNode);
        assertTrue(tagsNode.isArray());
        assertEquals(2, tagsNode.size());
        assertEquals("tag", tagsNode.get(0).asText());
        assertEquals("other", tagsNode.get(1).asText());

        // Verify that an audit entry was appended via EntityService.addItem
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
    }
}