package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AssignForReviewProcessorTest {

    @Test
    void sunnyDay_assigns_for_review() throws Exception {
        // Setup real object mapper and serializers (no Spring)
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

        // Prepare a valid Pet that is Available
        Pet pet = new Pet();
        pet.setPetId("pet-123");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("Available");
        pet.setAge(3);
        JsonNode petNode = objectMapper.valueToTree(pet);
        DataPayload petPayload = new DataPayload();
        petPayload.setData(petNode);

        // Prepare a valid User that is not suspended
        User user = new User();
        user.setUserId("user-abc");
        user.setFullName("Jane Doe");
        user.setEmail("jane@example.com");
        user.setRegisteredAt("2020-01-01T00:00:00Z");
        user.setStatus("ACTIVE");
        JsonNode userNode = objectMapper.valueToTree(user);
        DataPayload userPayload = new DataPayload();
        userPayload.setData(userNode);

        // Stub EntityService to return the pet and user found
        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));
        when(entityService.getItemsByCondition(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(userPayload)));

        // Instantiate the processor under test
        AssignForReviewProcessor processor = new AssignForReviewProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest payload that should pass isValid()
        AdoptionRequest requestEntity = new AdoptionRequest();
        requestEntity.setRequestId("req-1");
        requestEntity.setPetId("pet-123");
        requestEntity.setUserId("user-abc");
        requestEntity.setAdoptionFee(0.0);
        requestEntity.setHomeVisitRequired(false);
        requestEntity.setNotes(null);
        requestEntity.setPaymentStatus("NOT_PAID");
        requestEntity.setStatus("NEW");
        requestEntity.setRequestedAt("2023-01-01T00:00:00Z");

        JsonNode entityJson = objectMapper.valueToTree(requestEntity);
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AssignForReviewProcessor");
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

        assertNotNull(response.getPayload());
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData);

        // Processor should set status to PENDING_REVIEW
        assertEquals("PENDING_REVIEW", resultData.get("status").asText());

        // Notes should mention assigned for review and home visit flag
        String notes = resultData.get("notes").asText();
        assertTrue(notes.contains("Assigned for review"));
        assertTrue(notes.contains("Home visit required: false"));

        // Verify EntityService was used to look up Pet and User
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(), eq(true));
    }
}