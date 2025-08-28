package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
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

public class ArchivePetProcessorTest {

    @Test
    void sunnyDay_archiveAdoptedPet_updatesStatusAndCleansUserAdoptedList() throws Exception {
        // Arrange - ObjectMapper and real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a pet in Adopted state (valid per Pet.isValid)
        Pet pet = new Pet();
        pet.setPetId("pet-123");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("Adopted");
        // minimal valid values for required collections/maps are already set by constructor defaults

        JsonNode petJson = objectMapper.valueToTree(pet);

        // Prepare a user who has adoptedPetIds containing the pet.petId
        User user = new User();
        user.setUserId("user-1");
        user.setFullName("Alice");
        user.setEmail("alice@example.com");
        user.setRegisteredAt("2021-01-01T00:00:00Z");
        user.setStatus("Active");
        user.setAdoptedPetIds(List.of("pet-123"));

        JsonNode userJson = objectMapper.valueToTree(user);

        // Create DataPayload for the user list returned by EntityService.getItems(...)
        DataPayload userPayload = new DataPayload();
        userPayload.setData(userJson);
        ObjectNode userMeta = objectMapper.createObjectNode();
        // technical id used by ArchivePetProcessor to call updateItem
        String userTechnicalId = UUID.randomUUID().toString();
        userMeta.put("entityId", userTechnicalId);
        userPayload.setMeta(userMeta);

        // Stub getItems to return the single user payload
        when(entityService.getItems(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(userPayload)));

        // Stub updateItem to return the same UUID
        when(entityService.updateItem(eq(UUID.fromString(userTechnicalId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(userTechnicalId)));

        // Instantiate processor (no Spring)
        ArchivePetProcessor processor = new ArchivePetProcessor(serializerFactory, entityService, objectMapper);

        // Build request wrapping the pet JSON
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ArchivePetProcessor");
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

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload data to confirm status changed to "Archived"
        assertNotNull(response.getPayload());
        JsonNode resultingData = (JsonNode) response.getPayload().getData();
        assertNotNull(resultingData);
        assertEquals("Archived", resultingData.get("status").asText());

        // Verify that entityService.getItems was called to fetch users and updateItem was attempted
        verify(entityService, atLeastOnce()).getItems(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(), any(), any());
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(userTechnicalId)), any());
    }
}