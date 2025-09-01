package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.application.entity.owner.version_1.Owner;
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

public class NotifyOwnerProcessorTest {

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

        // Prepare an AdoptionJob that will be returned by EntityService.getItemsByCondition
        AdoptionJob job = new AdoptionJob();
        job.setId(UUID.randomUUID().toString());
        job.setOwnerId("owner-123");
        job.setCreatedAt("2020-01-01T00:00:00Z");
        job.setStatus("PENDING");
        job.setCriteria("{}");
        job.setResultCount(0);
        job.setResultsPreview(List.of()); // empty but non-null list is valid (AdoptionJob.isValid requires non-null list and that each element is non-blank; empty list OK)

        DataPayload jobPayload = new DataPayload();
        jobPayload.setData(objectMapper.valueToTree(job));
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("entityId", job.getId());
        jobPayload.setMeta(meta);

        when(entityService.getItemsByCondition(eq(AdoptionJob.ENTITY_NAME), eq(AdoptionJob.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(jobPayload)));

        when(entityService.updateItem(eq(UUID.fromString(job.getId())), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(job.getId())));

        // Instantiate processor (no Spring)
        NotifyOwnerProcessor processor = new NotifyOwnerProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Owner payload (must pass Owner.isValid)
        Owner owner = new Owner();
        owner.setId("owner-123");
        owner.setName("Jane Doe");
        owner.setContactEmail("jane.doe@example.com");

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(owner.getId());
        request.setProcessorName("NotifyOwnerProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(owner));
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should return success in sunny-day path");
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        // Ensure returned payload still represents the owner and contactEmail preserved
        Owner returned = objectMapper.treeToValue(response.getPayload().getData(), Owner.class);
        assertNotNull(returned);
        assertEquals(owner.getId(), returned.getId());
        assertEquals(owner.getContactEmail(), returned.getContactEmail());

        // Verify interactions with EntityService for sunny path
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(AdoptionJob.ENTITY_NAME), eq(AdoptionJob.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(job.getId())), any());
    }
}