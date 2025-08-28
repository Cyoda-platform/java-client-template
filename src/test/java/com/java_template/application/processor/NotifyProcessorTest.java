package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.CriterionSerializer;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class NotifyProcessorTest {

    @Test
    void sunnyDay_notify_processor_sets_completedAt_and_summary_and_resolves_owner_email() throws Exception {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of((CriterionSerializer) criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an Owner payload that will be returned by entityService.getItem(...)
        String ownerId = UUID.randomUUID().toString();
        Owner owner = new Owner();
        owner.setId(ownerId);
        owner.setName("Test Owner");
        owner.setEmail("owner@example.com");
        owner.setVerified(true);

        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(objectMapper.valueToTree(owner));
        // include meta.entityId so NotifyProcessor logging/message path can access it
        ownerPayload.setMeta(objectMapper.createObjectNode().put("entityId", ownerId));

        when(entityService.getItem(eq(UUID.fromString(ownerId))))
                .thenReturn(CompletableFuture.completedFuture(ownerPayload));

        // Construct the processor under test
        NotifyProcessor processor = new NotifyProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid IngestionJob that represents the sunny-day terminal status
        IngestionJob ingestionJob = new IngestionJob();
        ingestionJob.setRequestedBy(ownerId); // will be treated as UUID and resolved via entityService
        ingestionJob.setSourceUrl("http://example.com/source");
        ingestionJob.setStartedAt("2020-01-01T00:00:00Z");
        ingestionJob.setStatus("COMPLETED");
        ingestionJob.setCompletedAt(null); // processor should set this
        ingestionJob.setSummary(null); // processor should create default summary

        JsonNode ingestionJson = objectMapper.valueToTree(ingestionJob);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifyProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(ingestionJson);
        request.setPayload(payload);

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

        // Assert basics
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Extract resulting entity from response payload
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        IngestionJob result = objectMapper.treeToValue(response.getPayload().getData(), IngestionJob.class);
        assertNotNull(result);

        // Processor should have set completedAt (non-blank) and default summary with zeros
        assertNotNull(result.getCompletedAt());
        assertFalse(result.getCompletedAt().isBlank());

        assertNotNull(result.getSummary());
        assertEquals(0, result.getSummary().getCreated().intValue());
        assertEquals(0, result.getSummary().getUpdated().intValue());
        assertEquals(0, result.getSummary().getFailed().intValue());

        // Verify EntityService was used to resolve the owner by UUID
        verify(entityService, atLeastOnce()).getItem(eq(UUID.fromString(ownerId)));
        verifyNoMoreInteractions(entityService);
    }
}