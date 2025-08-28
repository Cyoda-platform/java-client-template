package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotificationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);

        // Prepare related Pet payload
        Pet pet = new Pet();
        pet.setId("PET-1");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setSex("Male");
        pet.setStatus("available");
        pet.setAge(3);

        DataPayload petPayload = new DataPayload();
        petPayload.setData(objectMapper.valueToTree(pet));
        List<DataPayload> petPayloads = List.of(petPayload);

        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(petPayloads));

        // Prepare related Owner payload
        Owner owner = new Owner();
        owner.setId("OWNER-1");
        owner.setFullName("Jane Doe");
        owner.setEmail("jane@example.com");
        owner.setPhone("1234567890");
        owner.setRole("user");
        owner.setCreatedAt(java.time.Instant.now());

        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(objectMapper.valueToTree(owner));
        List<DataPayload> ownerPayloads = List.of(ownerPayload);

        when(entityService.getItemsByCondition(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(ownerPayloads));

        NotificationProcessor processor = new NotificationProcessor(serializerFactory, entityService, objectMapper);

        // Prepare AdoptionRequest that is valid and in final state (approved) to trigger notifications
        AdoptionRequest requestEntity = new AdoptionRequest();
        requestEntity.setId("REQ-1");
        requestEntity.setPetId(pet.getId());
        requestEntity.setRequesterId(owner.getId());
        requestEntity.setStatus("approved");
        requestEntity.setSubmittedAt(java.time.Instant.now().toString());
        // message, processedAt, processedBy are left null to be set by processor

        JsonNode entityJson = objectMapper.valueToTree(requestEntity);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotificationProcessor");
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

        // Convert output payload back to AdoptionRequest to inspect changes
        AdoptionRequest outEntity = objectMapper.treeToValue(response.getPayload().getData(), AdoptionRequest.class);
        assertNotNull(outEntity);

        // processedAt should be set by the processor in sunny path
        assertNotNull(outEntity.getProcessedAt());
        assertFalse(outEntity.getProcessedAt().isBlank());

        // processedBy should be set to "notification-processor" as per logic when missing
        assertNotNull(outEntity.getProcessedBy());
        assertEquals("notification-processor", outEntity.getProcessedBy());

        // message should contain NotificationSent note with status
        assertNotNull(outEntity.getMessage());
        assertTrue(outEntity.getMessage().contains("NotificationSent:APPROVED"));
    }
}