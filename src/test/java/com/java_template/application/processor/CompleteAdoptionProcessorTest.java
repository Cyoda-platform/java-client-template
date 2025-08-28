package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class CompleteAdoptionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService as required
        EntityService entityService = mock(EntityService.class);

        // Prepare Pet that is AVAILABLE and has a technicalId (UUID)
        String petTechnicalId = UUID.randomUUID().toString();
        Pet pet = new Pet();
        pet.setTechnicalId(petTechnicalId);
        pet.setId("external-pet-1");
        pet.setName("Buddy");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");
        JsonNode petNode = objectMapper.valueToTree(pet);
        DataPayload petPayload = new DataPayload();
        petPayload.setData(petNode);

        // Stub getItem for pet retrieval by technicalId (UUID)
        when(entityService.getItem(eq(UUID.fromString(petTechnicalId))))
                .thenReturn(CompletableFuture.completedFuture(petPayload));

        // Stub updateItem for pet update to succeed
        when(entityService.updateItem(eq(UUID.fromString(petTechnicalId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(petTechnicalId)));

        // Prepare Owner payload including a technicalId field so processor can update it
        String ownerTechnicalId = UUID.randomUUID().toString();
        Owner owner = new Owner();
        owner.setOwnerId("owner-external-1");
        owner.setName("Jane Doe");
        owner.setContactEmail("jane@example.com");
        // Convert to JSON and add technicalId field (Owner class has no technicalId property)
        ObjectNode ownerNode = (ObjectNode) objectMapper.valueToTree(owner);
        ownerNode.put("technicalId", ownerTechnicalId);
        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(ownerNode);

        // Stub getItem for owner retrieval by technicalId (UUID)
        when(entityService.getItem(eq(UUID.fromString(ownerTechnicalId))))
                .thenReturn(CompletableFuture.completedFuture(ownerPayload));

        // Stub updateItem for owner update to succeed
        when(entityService.updateItem(eq(UUID.fromString(ownerTechnicalId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(ownerTechnicalId)));

        // Create processor under test
        CompleteAdoptionProcessor processor = new CompleteAdoptionProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest payload (must satisfy isValid())
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId(UUID.randomUUID().toString());
        adoptionRequest.setPetId(petTechnicalId); // refer to pet by technicalId (UUID)
        adoptionRequest.setRequesterId(ownerTechnicalId); // refer to owner by technicalId (UUID)
        adoptionRequest.setStatus("APPROVED");
        adoptionRequest.setSubmittedAt(java.time.Instant.now().toString());
        adoptionRequest.setNotes(null);

        JsonNode requestNode = objectMapper.valueToTree(adoptionRequest);
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(requestNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CompleteAdoptionProcessor");
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

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned AdoptionRequest payload
        assertNotNull(response.getPayload());
        Object returnedData = response.getPayload().getData();
        assertTrue(returnedData instanceof JsonNode);
        JsonNode returnedNode = (JsonNode) returnedData;
        assertEquals("COMPLETED", returnedNode.get("status").asText());
        assertNotNull(returnedNode.get("decisionAt"));
        assertTrue(!returnedNode.get("decisionAt").asText().isBlank());
        // Notes should contain success message fragment
        assertTrue(returnedNode.has("notes"));
        assertTrue(returnedNode.get("notes").asText().contains("Adoption completed successfully"));

        // Verify entityService interactions occurred for sunny path
        verify(entityService, atLeastOnce()).getItem(eq(UUID.fromString(petTechnicalId)));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(petTechnicalId)), any());
        verify(entityService, atLeastOnce()).getItem(eq(UUID.fromString(ownerTechnicalId)));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(ownerTechnicalId)), any());
    }
}