package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
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
import static org.mockito.Mockito.*;

public class NotifyRequesterProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare Owner payload returned by entityService
        Owner owner = new Owner();
        owner.setOwnerId("owner-1");
        owner.setName("Jane Doe");
        owner.setContactEmail("jane.doe@example.com");
        owner.setContactPhone("+123456789");

        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(objectMapper.valueToTree(owner));

        when(entityService.getItemsByCondition(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(ownerPayload)));

        // Prepare Pet payload returned by entityService
        Pet pet = new Pet();
        pet.setId("pet-1"); // external id matches adoption request petId
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");

        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));

        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));

        // Instantiate processor
        NotifyRequesterProcessor processor = new NotifyRequesterProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest entity (must pass isValid())
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId("req-1");
        adoptionRequest.setPetId("pet-1");
        adoptionRequest.setRequesterId("owner-1");
        adoptionRequest.setStatus("COMPLETED");
        adoptionRequest.setSubmittedAt("2020-01-01T00:00:00Z");

        JsonNode entityJson = objectMapper.valueToTree(adoptionRequest);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifyRequesterProcessor");
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

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // The processor does not modify the triggering AdoptionRequest entity; verify payload preserved
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("req-1", out.get("requestId").asText());
        assertEquals("COMPLETED", out.get("status").asText());

        // Verify entityService was invoked to fetch Owner and Pet
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
    }
}