package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.criterion.CriterionSerializer;
import com.java_template.common.serializer.processor.JacksonProcessorSerializer;
import com.java_template.common.serializer.criterion.JacksonCriterionSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PublishPetProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // EntityService is required by constructor - per instructions we may mock it
        EntityService entityService = mock(EntityService.class);

        PublishPetProcessor processor = new PublishPetProcessor(serializerFactory, entityService, objectMapper);

        // Build a Pet that is valid and in IMAGES_READY with valid photos so processor should set AVAILABLE
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID().toString());
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("IMAGES_READY");
        pet.setPhotos(List.of("http://example.com/photo1.jpg"));
        // intentionally leave importedAt null to let processor set it
        // healthNotes and size left null to trigger defaults

        JsonNode petJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-" + UUID.randomUUID());
        request.setRequestId("r1");
        request.setEntityId(pet.getId());
        request.setProcessorName("PublishPetProcessor");
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

        // Assert
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "response should indicate success");

        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "response payload data must be present");
        // status should have transitioned to AVAILABLE
        assertEquals("AVAILABLE", out.path("status").asText(), "pet status should be AVAILABLE in sunny path");
        // importedAt should be set by processor (non-empty)
        String importedAt = out.path("importedAt").asText();
        assertNotNull(importedAt);
        assertFalse(importedAt.isBlank(), "importedAt should be set and non-blank");
        // defaults applied
        assertEquals("Not specified", out.path("healthNotes").asText(), "healthNotes default should be applied");
        assertEquals("unknown", out.path("size").asText(), "size default should be applied");
    }
}