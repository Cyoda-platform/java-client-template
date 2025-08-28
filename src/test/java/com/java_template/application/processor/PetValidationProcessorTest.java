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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PetValidationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService per requirements. Ensure duplicate checks return empty lists.
        EntityService entityService = mock(EntityService.class);
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        PetValidationProcessor processor = new PetValidationProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid Pet entity (must satisfy isValid: name, species, status non-blank)
        Pet pet = new Pet();
        pet.setTechnicalId("tech-1");
        pet.setId("external-1");
        pet.setName("Buddy");
        pet.setSpecies("Dog");
        pet.setStatus("ACTIVE");
        pet.setBreed("Labrador");
        pet.setLocation("Springfield");

        // Convert entity to JsonNode for payload
        JsonNode entityJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PetValidationProcessor");
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

        DataPayload outPayload = response.getPayload();
        assertNotNull(outPayload);
        JsonNode outData = outPayload.getData();
        assertNotNull(outData);

        // Sunny-day: no duplicates found, status should remain as provided ("ACTIVE")
        assertEquals("ACTIVE", outData.get("status").asText());
        // Name and species should be preserved
        assertEquals("Buddy", outData.get("name").asText());
        assertEquals("Dog", outData.get("species").asText());

        // Verify that duplicate checks were performed (at least once)
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), anyInt(), any(), anyBoolean());
    }
}