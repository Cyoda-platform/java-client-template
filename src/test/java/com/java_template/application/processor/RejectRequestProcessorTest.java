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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RejectRequestProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
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

        // Prepare a Pet that is reserved by the AdoptionRequest (metadata.reservedBy == requestId)
        Pet pet = new Pet();
        pet.setName("Rover");
        pet.setPetId("pet-123");
        pet.setSpecies("Dog");
        pet.setStatus("Reserved");
        pet.setAge(3);
        pet.getHealthRecords().add("vaccine1");
        pet.getImages().add("img1");
        pet.getMetadata().put("reservedBy", "req-1");

        // Prepare DataPayload for the pet with meta containing technical entityId
        DataPayload petPayload = new DataPayload();
        JsonNode petData = objectMapper.valueToTree(pet);
        petPayload.setData(petData);
        UUID petTechnicalId = UUID.randomUUID();
        JsonNode petMeta = objectMapper.createObjectNode().put("entityId", petTechnicalId.toString());
        petPayload.setMeta(petMeta);

        // Stub getItemsByCondition to return the pet payload
        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));

        // Stub updateItem to succeed
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor instance using real serializerFactory and mocked entityService
        RejectRequestProcessor processor = new RejectRequestProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest that will be rejected and that reserved the pet
        AdoptionRequest requestEntity = new AdoptionRequest();
        requestEntity.setRequestId("req-1");
        requestEntity.setPetId("pet-123");
        requestEntity.setUserId("user-1");
        requestEntity.setRequestedAt("2025-01-01T00:00:00Z");
        requestEntity.setStatus("PENDING_REVIEW"); // reviewable state
        requestEntity.setPaymentStatus("PAID");
        requestEntity.setAdoptionFee(10.0);
        requestEntity.setHomeVisitRequired(false);

        // Wrap into DataPayload for the processor request
        JsonNode requestJson = objectMapper.valueToTree(requestEntity);
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(requestJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("RejectRequestProcessor");
        request.setPayload(requestPayload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic sunny-day expectations
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Response payload should contain the AdoptionRequest with status set to REJECTED
        assertNotNull(response.getPayload());
        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData);
        assertEquals("REJECTED", respData.get("status").asText());

        // Verify that the processor attempted to update the Pet reservation
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(petTechnicalId), any());
    }
}