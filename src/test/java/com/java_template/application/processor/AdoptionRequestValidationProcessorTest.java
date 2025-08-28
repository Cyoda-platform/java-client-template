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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AdoptionRequestValidationProcessorTest {

    @Test
    void sunnyDay_process_sets_under_review_when_pet_available_and_no_active_requests() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked per instructions
        EntityService entityService = mock(EntityService.class);

        // For adoption request search -> return empty list (no other active requests)
        when(entityService.getItemsByCondition(
                eq(AdoptionRequest.ENTITY_NAME),
                eq(AdoptionRequest.ENTITY_VERSION),
                any(),
                anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(new ArrayList<>()));

        // For pet lookup -> return a single pet with status "available"
        Pet pet = new Pet();
        pet.setId("PET-1");
        pet.setName("Fido");
        pet.setSpecies("dog");
        pet.setSex("male");
        pet.setStatus("available");
        pet.setAge(3);

        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));

        // Stub call specifically for Pet model; to keep it simple return for any matching modelName/version after the first stub
        when(entityService.getItemsByCondition(
                eq(Pet.ENTITY_NAME),
                eq(Pet.ENTITY_VERSION),
                any(),
                anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));

        // Create processor instance (uses real serializerFactory and objectMapper)
        AdoptionRequestValidationProcessor processor =
                new AdoptionRequestValidationProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest entity (must pass isValid())
        AdoptionRequest reqEntity = new AdoptionRequest();
        reqEntity.setId("REQ-1");
        reqEntity.setPetId("PET-1");
        reqEntity.setRequesterId("OWNER-1");
        reqEntity.setStatus("submitted");
        reqEntity.setSubmittedAt("2025-01-01T12:00:00Z");

        JsonNode entityJson = objectMapper.valueToTree(reqEntity);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AdoptionRequestValidationProcessor");
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Sunny path: status should be moved to "under_review"
        assertEquals("under_review", out.get("status").asText());
    }
}