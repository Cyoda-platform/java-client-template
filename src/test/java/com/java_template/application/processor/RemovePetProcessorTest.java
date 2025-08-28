package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class RemovePetProcessorTest {

    @Test
    void sunnyDay_removePet_processor_marks_pet_removed_and_updates_owners() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a pet that is valid and not yet removed
        Pet pet = new Pet();
        String petTechnicalId = UUID.randomUUID().toString();
        pet.setTechnicalId(petTechnicalId);
        pet.setId("pet-external-1");
        pet.setName("Buddy");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE"); // initial status
        // No need to set all fields; isValid requires name, species, status

        // Prepare an owner referencing the pet by technicalId in savedPets and adoptedPets
        Owner owner = new Owner();
        String ownerTechnicalId = UUID.randomUUID().toString();
        owner.setOwnerId("owner-external-1");
        owner.setName("Alice");
        owner.setContactEmail("alice@example.com");
        owner.setSavedPets(List.of(petTechnicalId));
        owner.setAdoptedPets(List.of()); // empty adoptedPets for simplicity

        // Create DataPayload for the owner as would be returned from getItems()
        DataPayload ownerPayload = new DataPayload();
        JsonNode ownerJson = objectMapper.valueToTree(owner);
        ownerPayload.setData(ownerJson);
        ownerPayload.setId(UUID.fromString(ownerTechnicalId)); // this will be used to parse UUID for update

        // Stub getItems to return the owner payload
        when(entityService.getItems(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(ownerPayload)));

        // Stub updateItem to complete successfully
        when(entityService.updateItem(eq(UUID.fromString(ownerTechnicalId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(ownerTechnicalId)));

        // Instantiate processor
        RemovePetProcessor processor = new RemovePetProcessor(serializerFactory, entityService, objectMapper);

        // Build request payload containing the pet entity (use real entity to JSON conversion)
        JsonNode petJson = objectMapper.valueToTree(pet);
        DataPayload payload = new DataPayload();
        payload.setData(petJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(pet.getTechnicalId());
        request.setProcessorName("RemovePetProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload: status should be set to REMOVED
        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("REMOVED", out.get("status").asText());

        // Verify that owners were fetched and updateItem was invoked for the owner we provided
        verify(entityService, atLeastOnce()).getItems(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), any(), any());
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(ownerTechnicalId)), any());
    }
}