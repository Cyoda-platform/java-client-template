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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AdoptPetProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a Pet that is valid and has an external id (so petRef will be pet.id)
        Pet pet = new Pet();
        pet.setId("pet-123");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");

        JsonNode petJson = objectMapper.valueToTree(pet);

        // Prepare an approved AdoptionRequest referencing the pet and a requester (owner business id)
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId("req-1");
        adoptionRequest.setPetId("pet-123");
        adoptionRequest.setRequesterId("owner-abc");
        adoptionRequest.setStatus("approved");
        adoptionRequest.setSubmittedAt("2025-01-01T00:00:00Z");
        DataPayload adoptionPayload = new DataPayload();
        adoptionPayload.setData(objectMapper.valueToTree(adoptionRequest));

        // Prepare an Owner entity matching requesterId that will be returned by search
        Owner owner = new Owner();
        owner.setOwnerId("owner-abc");
        owner.setName("Jane Doe");
        owner.setContactEmail("jane@example.com");
        owner.setAdoptedPets(null); // processor will create list and add petRef
        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(objectMapper.valueToTree(owner));

        // Stub entityService getItemsByCondition for AdoptionRequest and Owner searches
        when(entityService.getItemsByCondition(
                eq(AdoptionRequest.ENTITY_NAME), eq(AdoptionRequest.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(adoptionPayload)));

        when(entityService.getItemsByCondition(
                eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(ownerPayload)));

        // Construct processor with mocked EntityService
        AdoptPetProcessor processor = new AdoptPetProcessor(serializerFactory, entityService, objectMapper);

        // Build request and context
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AdoptPetProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(petJson);
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
        assertTrue(response.getSuccess(), "Processor should report success in sunny-day path");

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("ADOPTED", out.get("status").asText(), "Pet status should be set to ADOPTED in sunny path");

        // Verify that EntityService searches were performed for AdoptionRequest and Owner
        verify(entityService, atLeastOnce()).getItemsByCondition(
                eq(AdoptionRequest.ENTITY_NAME), eq(AdoptionRequest.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).getItemsByCondition(
                eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true));
    }
}