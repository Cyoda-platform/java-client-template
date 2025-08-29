package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.audit.version_1.Audit;
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

public class GdprTransferTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real, not mocked)
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

        // Prepare a User that will be the input entity (must pass isValid())
        User user = new User();
        user.setUserId("user-123");
        user.setEmail("user@example.com");

        // Prepare a Post owned by that user to be returned by getItemsByCondition
        Post post = new Post();
        post.setId("post-1");
        post.setOwner_id(user.getUserId());
        post.setTitle("A Title");
        post.setSlug("a-title");
        post.setStatus("published");

        DataPayload postPayload = new DataPayload();
        postPayload.setData(objectMapper.valueToTree(post));
        // include meta.entityId so processor can call updateItem
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("entityId", UUID.randomUUID().toString());
        postPayload.setMeta(meta);

        // Stub entityService interactions for sunny day
        when(entityService.getItemsByCondition(eq(Post.ENTITY_NAME), eq(Post.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(postPayload)));

        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor instance (real)
        GdprTransfer processor = new GdprTransfer(serializerFactory, entityService, objectMapper);

        // Build request with payload containing the User entity JSON
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("GdprTransfer");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        // Assert payload has gdprState set to "transferred"
        assertNotNull(response.getPayload(), "Response payload should be present");
        assertNotNull(response.getPayload().getData(), "Response payload data should be present");
        assertEquals("transferred", response.getPayload().getData().get("gdprState").asText());

        // Verify entityService interactions occurred (optional sanity checks)
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Post.ENTITY_NAME), eq(Post.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any());
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
    }
}