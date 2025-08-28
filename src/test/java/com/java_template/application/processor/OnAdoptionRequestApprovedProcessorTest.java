package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
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

public class OnAdoptionRequestApprovedProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock EntityService (allowed by policy)
        EntityService entityService = mock(EntityService.class);

        // Prepare a Pet that will be returned by getItemsByCondition
        Pet pet = new Pet();
        pet.setId("PET-123");
        pet.setName("Buddy");
        pet.setSpecies("dog");
        pet.setStatus("available");
        pet.setSex("male");
        pet.setAge(3);

        UUID technicalId = UUID.randomUUID();

        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));
        // setTechnicalId should be available on DataPayload
        petPayload.setTechnicalId(technicalId.toString());

        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(technicalId));

        // Create processor under test
        OnAdoptionRequestApprovedProcessor processor =
                new OnAdoptionRequestApprovedProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest entity (use entity object, then serialize to payload)
        AdoptionRequest requestEntity = new AdoptionRequest();
        requestEntity.setId("REQ-1");
        requestEntity.setPetId("PET-123");
        requestEntity.setRequesterId("OWNER-1");
        requestEntity.setStatus("approved");
        requestEntity.setSubmittedAt("2025-01-01T00:00:00Z");

        JsonNode entityJson = objectMapper.valueToTree(requestEntity);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("OnAdoptionRequestApprovedProcessor");
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

        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        JsonNode out = response.getPayload().getData();
        assertEquals("completed", out.get("status").asText());
        assertNotNull(out.get("processedAt"));
        assertFalse(out.get("processedAt").asText().isBlank());

        // Verify EntityService interactions happened as expected
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(technicalId), any());
    }
}