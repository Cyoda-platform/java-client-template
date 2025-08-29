package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.media.version_1.Media;
import com.java_template.application.entity.post.version_1.Post;
import com.java_template.application.entity.postversion.version_1.PostVersion;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PublishPostTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
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

        // Prepare a Post with a current_version_id that will be fetched
        String postId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();

        Post post = new Post();
        post.setId(postId);
        post.setOwner_id(UUID.randomUUID().toString());
        post.setTitle("Test Title");
        post.setSlug("test-title");
        post.setStatus("draft");
        post.setCurrent_version_id(versionId);
        post.setAuthor_id(UUID.randomUUID().toString());
        // media_refs is null initially to test creation

        // Prepare a PostVersion that is valid and matches versionId
        PostVersion pv = new PostVersion();
        pv.setVersion_id(versionId);
        pv.setPost_id(postId);
        pv.setCreated_at(Instant.now().toString());
        pv.setContent_rich("<p>Hello world</p>");
        pv.setNormalized_text(null); // expect processor to normalize

        JsonNode pvNode = objectMapper.valueToTree(pv);
        DataPayload pvPayload = new DataPayload();
        pvPayload.setData(pvNode);

        // Stub entityService.getItem(...) to return the PostVersion payload
        when(entityService.getItem(eq(UUID.fromString(versionId))))
                .thenReturn(CompletableFuture.completedFuture(pvPayload));

        // Stub updateItem for PostVersion (called to request embeddings/finalize)
        when(entityService.updateItem(eq(UUID.fromString(versionId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(versionId)));

        // Stub addItem for Media and Audit to succeed
        when(entityService.addItem(eq(Media.ENTITY_NAME), eq(Media.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
        when(entityService.addItem(eq("Audit"), eq(1), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor with real serializerFactory and mocked EntityService
        PublishPost processor = new PublishPost(serializerFactory, entityService, objectMapper);

        // Build request containing the Post as payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(postId);
        request.setProcessorName("PublishPost");
        DataPayload requestPayload = new DataPayload();
        JsonNode postJson = objectMapper.valueToTree(post);
        requestPayload.setData(postJson);
        request.setPayload(requestPayload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Extract resulting Post entity from response payload and assert expected sunny-day mutations
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        JsonNode resultData = response.getPayload().getData();
        Post resultPost = objectMapper.treeToValue(resultData, Post.class);

        assertNotNull(resultPost);
        assertEquals("published", resultPost.getStatus());
        assertNotNull(resultPost.getPublished_at());
        assertEquals("public, max-age=3600", resultPost.getCache_control());
        // media_refs should have gained one entry for the created bundle
        assertNotNull(resultPost.getMedia_refs());
        assertFalse(resultPost.getMedia_refs().isEmpty());

        // Verify EntityService interactions for sunny path
        verify(entityService, atLeastOnce()).getItem(eq(UUID.fromString(versionId)));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(versionId)), any());
        verify(entityService, atLeastOnce()).addItem(eq(Media.ENTITY_NAME), eq(Media.ENTITY_VERSION), any());
        verify(entityService, atLeastOnce()).addItem(eq("Audit"), eq(1), any());
    }
}