package com.java_template.application.processor;

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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TransferOwnershipProcessorTest {

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

        // Prepare pet and adoption request
        String petUuid = UUID.randomUUID().toString();

        Pet pet = new Pet();
        pet.setId(petUuid);
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");

        // EntityService.getItem should return DataPayload containing the Pet
        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));
        when(entityService.getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), eq(UUID.fromString(petUuid))))
                .thenReturn(CompletableFuture.completedFuture(petPayload));

        // EntityService.updateItem should succeed
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(petUuid)));

        // Build adoption request that passes isValid()
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setId("req-1");
        adoptionRequest.setPetId(petUuid);
        adoptionRequest.setRequesterName("Jane Doe");
        adoptionRequest.setContactEmail("jane@example.com");
        adoptionRequest.setStatus("APPROVED"); // processor only proceeds when APPROVED
        adoptionRequest.setSubmittedAt("2025-01-01T00:00:00Z");

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("TransferOwnershipProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(adoptionRequest));
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Create processor using real serializers and mocked EntityService
        TransferOwnershipProcessor processor = new TransferOwnershipProcessor(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertEquals("COMPLETED", out.get("status").asText());
        assertEquals("system", out.get("processedBy").asText());
        assertTrue(out.get("notes").asText().contains(petUuid));

        // Verify entity service interactions happened
        verify(entityService, atLeastOnce()).getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), eq(UUID.fromString(petUuid)));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(petUuid)), any());
    }
}