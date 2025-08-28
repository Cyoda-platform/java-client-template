package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FinalizeAdoptionProcessorTest {

    @Test
    void sunnyDay_finalize_adoption_processor() throws Exception {
        // Arrange - ObjectMapper and serializers (real, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown props as requested
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid AdoptionRequest that will trigger the sunny-path finalization
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId("req-123");
        adoptionRequest.setPetId("pet-business-1");
        adoptionRequest.setUserId("user-business-1");
        adoptionRequest.setRequestedAt("2025-01-01T00:00:00Z");
        adoptionRequest.setStatus("PENDING");
        adoptionRequest.setPaymentStatus("PAID"); // needed to proceed
        adoptionRequest.setAdoptionFee(25.0);
        adoptionRequest.setHomeVisitRequired(false);
        JsonNode adoptionJson = objectMapper.valueToTree(adoptionRequest);

        // Prepare Pet entity JSON for returned pet payload (must be valid per Pet.isValid)
        Pet pet = new Pet();
        pet.setPetId("pet-business-1");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("Available");
        // lists and metadata are initialized in constructor
        JsonNode petJson = objectMapper.valueToTree(pet);
        ObjectNode petMeta = objectMapper.createObjectNode();
        petMeta.put("entityId", UUID.randomUUID().toString());
        DataPayload petPayload = new DataPayload();
        petPayload.setData(petJson);
        petPayload.setMeta(petMeta);

        // Prepare User entity JSON for returned user payload (must be valid per User.isValid)
        User user = new User();
        user.setUserId("user-business-1");
        user.setFullName("Jane Doe");
        user.setEmail("jane@example.com");
        user.setRegisteredAt("2024-01-01T00:00:00Z");
        user.setStatus("ACTIVE");
        JsonNode userJson = objectMapper.valueToTree(user);
        ObjectNode userMeta = objectMapper.createObjectNode();
        userMeta.put("entityId", UUID.randomUUID().toString());
        DataPayload userPayload = new DataPayload();
        userPayload.setData(userJson);
        userPayload.setMeta(userMeta);

        // Stub EntityService.getItemsByCondition for Pet and User
        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));
        when(entityService.getItemsByCondition(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(userPayload)));

        // Stub updateItem to simulate successful updates
        when(entityService.updateItem(any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor with real serializers and mocked EntityService
        FinalizeAdoptionProcessor processor = new FinalizeAdoptionProcessor(serializerFactory, entityService, objectMapper);

        // Build request and context
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FinalizeAdoptionProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(adoptionJson);
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor should report success in sunny-path");

        // Inspect returned payload for expected state: adoption request should be marked COMPLETED
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response payload data should not be null");
        assertEquals("COMPLETED", responseData.get("status").asText(), "AdoptionRequest should be marked COMPLETED");

        // Verify that pet and user updates were attempted
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
    }
}