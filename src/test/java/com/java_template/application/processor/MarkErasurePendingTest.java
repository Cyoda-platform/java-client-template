package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.post.version_1.Post;
import com.java_template.application.entity.user.version_1.User;
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

public class MarkErasurePendingTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Stub addItem to return a UUID for audits
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Stub updateItem to return a UUID for post updates
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Prepare a User that passes validation
        User user = new User();
        user.setUserId("user-123");
        user.setEmail("user@example.com");
        user.setGdprState(null); // will be set by processor

        // Prepare a Post that will be returned by getItemsByCondition
        Post post = new Post();
        post.setId("post-1");
        post.setOwner_id(user.getUserId());
        post.setTitle("Title");
        post.setSlug("title");
        post.setStatus("published");

        // Create DataPayload for the post with meta.entityId used by processor
        DataPayload postPayload = new DataPayload();
        postPayload.setData(objectMapper.valueToTree(post));
        ObjectNode metaNode = objectMapper.createObjectNode();
        UUID technicalId = UUID.randomUUID();
        metaNode.put("entityId", technicalId.toString());
        postPayload.setMeta(metaNode);

        // Stub getItemsByCondition to return the post payload list
        when(entityService.getItemsByCondition(eq(Post.ENTITY_NAME), eq(Post.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(postPayload)));

        // Instantiate processor (real)
        MarkErasurePending processor = new MarkErasurePending(serializerFactory, entityService, objectMapper);

        // Build request with payload containing the User
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("MarkErasurePending");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(user));
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

        // Verify payload returned contains updated gdprState
        assertNotNull(response.getPayload());
        JsonNode returned = response.getPayload().getData();
        assertNotNull(returned);
        assertEquals("erased_pending", returned.get("gdprState").asText());

        // Verify EntityService interactions (at least once)
        verify(entityService, atLeastOnce()).addItem(eq("Audit"), eq(1), any());
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Post.ENTITY_NAME), eq(Post.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(technicalId), any());
    }
}