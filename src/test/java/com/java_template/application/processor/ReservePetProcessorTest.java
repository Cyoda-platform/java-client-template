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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ReservePetProcessorTest {

    @Test
    void sunnyDay_reservePet_setsReservedAndAssociatesAdoptionRequest() throws Exception {
        // Setup real ObjectMapper per instructions
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

        // Prepare an AdoptionRequest that should be found by the processor
        AdoptionRequest foundRequest = new AdoptionRequest();
        foundRequest.setRequestId("req-123");
        foundRequest.setPetId("pet-1");
        foundRequest.setUserId("user-1");
        foundRequest.setAdoptionFee(0.0);
        foundRequest.setHomeVisitRequired(false);
        foundRequest.setRequestedAt("2020-01-01T00:00:00Z");
        foundRequest.setStatus("CREATED");
        foundRequest.setPaymentStatus("PAID");

        DataPayload adoptionPayload = new DataPayload();
        adoptionPayload.setData(objectMapper.valueToTree(foundRequest));

        when(entityService.getItemsByCondition(
                eq(AdoptionRequest.ENTITY_NAME),
                eq(AdoptionRequest.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(adoptionPayload)));

        // Instantiate processor with real serializers and mocked EntityService
        ReservePetProcessor processor = new ReservePetProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid Pet entity JSON that passes isValid() and is "Available"
        Pet pet = new Pet();
        pet.setPetId("pet-1");
        pet.setName("Fluffy");
        pet.setSpecies("Cat");
        pet.setStatus("Available");
        pet.setCreatedAt("2020-01-01T00:00:00Z");
        pet.setAge(2);

        JsonNode petJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ReservePetProcessor");
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

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload data for expected sunny-day changes
        assertNotNull(response.getPayload());
        JsonNode dataNode = response.getPayload().getData();
        assertNotNull(dataNode);

        // Status should be changed to Reserved
        assertEquals("Reserved", dataNode.get("status").asText());

        // Metadata should include reservedAt and reservedByRequestId matching foundRequest.requestId
        JsonNode metadataNode = dataNode.get("metadata");
        assertNotNull(metadataNode);
        assertTrue(metadataNode.has("reservedAt"));
        assertEquals("req-123", metadataNode.get("reservedByRequestId").asText());
    }
}