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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ApproveRequestProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a Pet that is available and will be reserved by the processor
        String petTechnicalId = UUID.randomUUID().toString();
        Pet pet = new Pet();
        pet.setId(petTechnicalId);
        pet.setPetId("external-" + petTechnicalId);
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("Available");
        // metadata initialized by default in Pet constructor

        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));

        when(entityService.getItem(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(petPayload));

        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(petTechnicalId)));

        // Create AdoptionRequest that passes isValid() and has a fee > 0 to exercise payment pending path
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId("req-123");
        adoptionRequest.setPetId(petTechnicalId); // technical UUID string
        adoptionRequest.setUserId("user-1");
        adoptionRequest.setRequestedAt("2025-01-01T00:00:00Z");
        adoptionRequest.setStatus("NEW");
        adoptionRequest.setPaymentStatus("NEW");
        adoptionRequest.setAdoptionFee(25.0);
        adoptionRequest.setHomeVisitRequired(false);

        JsonNode requestEntityJson = objectMapper.valueToTree(adoptionRequest);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ApproveRequestProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(requestEntityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Instantiate processor with real serializerFactory and mocked entityService
        ApproveRequestProcessor processor = new ApproveRequestProcessor(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload for expected state changes on the AdoptionRequest
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);
        assertEquals("APPROVED", returnedData.get("status").asText());
        assertEquals("PENDING", returnedData.get("paymentStatus").asText());

        // Verify that the processor attempted to reserve the pet by updating it to "Reserved"
        ArgumentCaptor<Pet> petCaptor = ArgumentCaptor.forClass(Pet.class);
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(petTechnicalId)), petCaptor.capture());
        Pet updatedPet = petCaptor.getValue();
        assertNotNull(updatedPet);
        assertEquals("Reserved", updatedPet.getStatus());
        // verify metadata contains reservation info
        assertNotNull(updatedPet.getMetadata());
        assertEquals("req-123", String.valueOf(updatedPet.getMetadata().get("reservedBy")));
    }
}