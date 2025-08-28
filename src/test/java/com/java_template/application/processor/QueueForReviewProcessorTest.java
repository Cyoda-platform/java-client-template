package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
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
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class QueueForReviewProcessorTest {

    @Test
    void sunnyDay_queueForReview_process_test() throws Exception {
        // Arrange - serializer setup
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare Owner payload (verified)
        Owner owner = new Owner();
        owner.setOwnerId("owner-123");
        owner.setName("Jane Doe");
        owner.setContactEmail("jane@example.com");
        owner.setVerificationStatus("verified");
        JsonNode ownerNode = objectMapper.valueToTree(owner);
        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(ownerNode);

        when(entityService.getItemsByCondition(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(ownerPayload)));

        // Prepare Pet payload (available)
        Pet pet = new Pet();
        pet.setName("Fido");
        pet.setSpecies("dog");
        pet.setStatus("available");
        JsonNode petNode = objectMapper.valueToTree(pet);
        DataPayload petPayload = new DataPayload();
        petPayload.setData(petNode);

        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));

        // Instantiate processor
        QueueForReviewProcessor processor = new QueueForReviewProcessor(serializerFactory, entityService, objectMapper);

        // Build AdoptionRequest that passes isValid()
        AdoptionRequest reqEntity = new AdoptionRequest();
        reqEntity.setRequestId("req-1");
        reqEntity.setPetId("pet-1");
        reqEntity.setRequesterId("owner-123");
        reqEntity.setStatus("submitted");
        reqEntity.setSubmittedAt("2025-01-01T00:00:00Z");

        JsonNode requestEntityJson = objectMapper.valueToTree(reqEntity);
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(requestEntityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("QueueForReviewProcessor");
        request.setPayload(requestPayload);

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
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Expect status to be set to "under_review" in the sunny-day path
        assertEquals("under_review", out.get("status").asText());
        // decisionAt should remain null / missing for queued requests
        JsonNode decisionAt = out.get("decisionAt");
        assertTrue(decisionAt == null || decisionAt.isNull());
    }
}