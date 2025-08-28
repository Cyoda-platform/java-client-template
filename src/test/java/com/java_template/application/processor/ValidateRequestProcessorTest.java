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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ValidateRequestProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real Jackson serializers and factory
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

        // Prepare referenced Pet and User that will be returned by EntityService.getItem
        UUID petUuid = UUID.randomUUID();
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID().toString());
        pet.setName("Fido");
        pet.setPetId("external-pet-id");
        pet.setSpecies("Dog");
        pet.setStatus("Available");
        pet.setAge(3);
        // ensure collections / maps not null
        pet.getHealthRecords().clear();
        pet.getImages().clear();
        pet.getMetadata().clear();

        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));

        UUID userUuid = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUserId("user-external-id");
        user.setFullName("Jane Doe");
        user.setEmail("jane.doe@example.com");
        user.setRegisteredAt("2025-01-01T00:00:00Z");
        user.setStatus("Active");
        DataPayload userPayload = new DataPayload();
        userPayload.setData(objectMapper.valueToTree(user));

        // Stub entityService.getItem for the two UUIDs
        when(entityService.getItem(eq(petUuid))).thenReturn(CompletableFuture.completedFuture(petPayload));
        when(entityService.getItem(eq(userUuid))).thenReturn(CompletableFuture.completedFuture(userPayload));

        // Build the processor under test
        ValidateRequestProcessor processor = new ValidateRequestProcessor(serializerFactory, entityService, objectMapper);

        // Build AdoptionRequest payload that passes isValid()
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setRequestId("req-123");
        adoptionRequest.setPetId(petUuid.toString());   // will be parsed by processor
        adoptionRequest.setUserId(userUuid.toString()); // will be parsed by processor
        adoptionRequest.setAdoptionFee(25.0);
        adoptionRequest.setHomeVisitRequired(false);
        adoptionRequest.setNotes(null); // blank so processor will set default
        adoptionRequest.setPaymentStatus("PAID");
        adoptionRequest.setStatus("NEW"); // initial status (will be changed to PENDING_REVIEW)
        adoptionRequest.setRequestedAt("2025-08-01T12:00:00Z");

        JsonNode requestNode = objectMapper.valueToTree(adoptionRequest);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidateRequestProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(requestNode);
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

        // Inspect returned payload for expected sunny-day state changes
        assertNotNull(response.getPayload());
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData);

        // Processor should set status to PENDING_REVIEW and default notes when validation passes
        assertEquals("PENDING_REVIEW", outData.get("status").asText());
        assertEquals("Validated and assigned for review", outData.get("notes").asText());
    }
}