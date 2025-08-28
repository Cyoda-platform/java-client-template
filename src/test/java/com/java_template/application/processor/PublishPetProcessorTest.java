package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PublishPetProcessorTest {

    @Test
    void sunnyDay_process_setsStatusToAvailable() throws Exception {
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
        // When duplicate-check is executed, return empty list => no duplicates
        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(new ArrayList<DataPayload>()));

        PublishPetProcessor processor = new PublishPetProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Pet entity that will pass validation and trigger the status transition logic.
        // Use a non-canonical status (e.g., "PUBLISHED") so processor will transition it to "available".
        Pet pet = new Pet();
        pet.setTechnicalId(UUID.randomUUID().toString());
        pet.setId("external-1");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setBreed("Beagle");
        pet.setLocation("Shelter-1");
        pet.setStatus("PUBLISHED"); // non-canonical -> should be transitioned to "available"

        JsonNode entityJson = objectMapper.valueToTree(pet);

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PublishPetProcessor");
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
        assertTrue(response.getSuccess(), "Processor should succeed in sunny-day path");

        assertNotNull(response.getPayload(), "Response payload should be present");
        assertNotNull(response.getPayload().getData(), "Response payload data should be present");

        // Convert payload back to Pet and verify status was transitioned to "available"
        Pet out = objectMapper.treeToValue(response.getPayload().getData(), Pet.class);
        assertNotNull(out);
        assertEquals("available", out.getStatus(), "Pet status should be set to 'available' in sunny-day path");

        // Verify duplicate-check was invoked
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
    }
}