package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PetStatusUpdateProcessorTest {

    @Test
    void sunnyDay_process_updates_pet_status_and_returns_success() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService (required by processor constructor)
        EntityService entityService = mock(EntityService.class);

        // Prepare a Pet payload that will be returned by EntityService.getItemsByCondition
        String petBusinessId = "BUS-1";
        String petTechnicalId = UUID.randomUUID().toString();

        Pet pet = new Pet();
        pet.setId(petBusinessId); // business id used in condition search ($.id)
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setSex("Male");
        pet.setStatus("available");
        pet.setAge(3);

        // Create DataPayload for the pet
        DataPayload petPayload = new DataPayload();
        // set data node
        JsonNode petDataNode = objectMapper.valueToTree(pet);
        petPayload.setData(petDataNode);
        // set both technicalId and id fields used by processor
        petPayload.setTechnicalId(petTechnicalId);
        petPayload.setId(petBusinessId);

        when(entityService.getItemsByCondition(
                eq(Pet.ENTITY_NAME),
                eq(Pet.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));

        // Stub updateItem to return the technical UUID
        when(entityService.updateItem(any(UUID.class), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return CompletableFuture.completedFuture(id);
        });

        PetStatusUpdateProcessor processor = new PetStatusUpdateProcessor(serializerFactory, entityService, objectMapper);

        // Build AdoptionRequest that will trigger pet status change to "pending"
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setId("REQ-1");
        adoptionRequest.setPetId(petBusinessId); // matches petPayload.data.id
        adoptionRequest.setRequesterId("USER-1");
        adoptionRequest.setStatus("submitted"); // should cause pet.status -> "pending"
        adoptionRequest.setSubmittedAt("2025-01-01T00:00:00Z");

        JsonNode reqNode = objectMapper.valueToTree(adoptionRequest);
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(reqNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PetStatusUpdateProcessor");
        request.setPayload(requestPayload);

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

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // The processor returns the AdoptionRequest as payload; ensure it is present and unchanged
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        JsonNode outNode = response.getPayload().getData();
        assertEquals("REQ-1", outNode.get("id").asText());
        assertEquals("submitted", outNode.get("status").asText());

        // Verify that the EntityService was asked to update the Pet with status changed to "pending"
        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Pet> petCaptor = ArgumentCaptor.forClass(Pet.class);
        verify(entityService, atLeastOnce()).updateItem(uuidCaptor.capture(), petCaptor.capture());

        Pet updatedPet = petCaptor.getValue();
        assertNotNull(updatedPet);
        assertEquals("pending", updatedPet.getStatus());
        // Ensure update was invoked for the technicalId we provided
        assertEquals(UUID.fromString(petTechnicalId), uuidCaptor.getValue());
    }
}