package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.audit.version_1.Audit;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AppendAuditTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real, no Spring)
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

        // Prepare persisted audit id returned by addItem
        UUID persistedAuditId = UUID.randomUUID();
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(persistedAuditId));

        // Prepare a referenced User to be returned by getItem and allow updateItem
        UUID userUuid = UUID.randomUUID();
        User existingUser = new User();
        existingUser.setUserId(userUuid.toString());
        existingUser.setEmail("user@example.com");
        existingUser.setAuditRefs(new ArrayList<>()); // start empty

        DataPayload userPayload = new DataPayload();
        userPayload.setData(objectMapper.valueToTree(existingUser));

        when(entityService.getItem(eq(userUuid))).thenReturn(CompletableFuture.completedFuture(userPayload));
        when(entityService.updateItem(eq(userUuid), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor with real serializerFactory and mocked EntityService
        AppendAudit processor = new AppendAudit(serializerFactory, entityService, objectMapper);

        // Build a valid Audit entity JSON payload (must pass isValid() during validation)
        Audit inputAudit = new Audit();
        inputAudit.setAuditId(UUID.randomUUID().toString());
        inputAudit.setAction("create");
        inputAudit.setActorId(UUID.randomUUID().toString());
        // set entityRef to reference a User so processor will attempt to append audit ref
        inputAudit.setEntityRef(userUuid.toString() + ":User");
        inputAudit.setTimestamp("2025-01-01T00:00:00Z");
        inputAudit.setMetadata(null);

        // Convert to JsonNode and put into DataPayload
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(inputAudit));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AppendAudit");
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

        // Assert payload contains the audit and fields match input (sunny path)
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        Audit returned = objectMapper.treeToValue(response.getPayload().getData(), Audit.class);
        assertEquals(inputAudit.getAuditId(), returned.getAuditId());
        assertEquals(inputAudit.getAction(), returned.getAction());
        assertEquals(inputAudit.getActorId(), returned.getActorId());
        assertEquals(inputAudit.getEntityRef(), returned.getEntityRef());
        assertEquals(inputAudit.getTimestamp(), returned.getTimestamp());

        // Verify EntityService interactions: addItem was called and user update was attempted
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any(Audit.class));
        verify(entityService, atLeastOnce()).getItem(eq(userUuid));
        verify(entityService, atLeastOnce()).updateItem(eq(userUuid), any());
    }
}