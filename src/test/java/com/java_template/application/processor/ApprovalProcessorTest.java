package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ApprovalProcessorTest {

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

        // Only EntityService may be mocked
        EntityService entityService = mock(EntityService.class);

        // Prepare Pet (available) and Owner (verified) results to be returned by EntityService
        Pet pet = new Pet();
        pet.setName("Fido");
        pet.setSpecies("dog");
        pet.setStatus("available");
        pet.setId("pet-1");

        Owner owner = new Owner();
        owner.setOwnerId("owner-1");
        owner.setName("Alice");
        owner.setContactEmail("alice@example.com");
        owner.setVerificationStatus("verified");

        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));
        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(objectMapper.valueToTree(owner));

        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));
        when(entityService.getItemsByCondition(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(ownerPayload)));

        // Instantiate processor (use real serializer factory and object mapper)
        ApprovalProcessor processor = new ApprovalProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid AdoptionRequest (must pass isValid())
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId("req-1");
        adoptionRequest.setPetId("pet-1");
        adoptionRequest.setRequesterId("owner-1");
        adoptionRequest.setStatus("under_review");
        adoptionRequest.setSubmittedAt(Instant.now().toString());

        // Build request payload
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(objectMapper.valueToTree(adoptionRequest));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(adoptionRequest.getRequestId());
        request.setProcessorName("ApprovalProcessor");
        request.setPayload(requestPayload);

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

        // Inspect payload for expected sunny-day state changes: status -> approved, reviewerId set, decisionAt set
        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("approved", out.get("status").asText().toLowerCase());
        assertNotNull(out.get("reviewerId"));
        assertFalse(out.get("reviewerId").asText().isBlank());
        assertNotNull(out.get("decisionAt"));
        assertFalse(out.get("decisionAt").asText().isBlank());

        // Verify EntityService was called to fetch Pet and Owner
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true));
    }
}