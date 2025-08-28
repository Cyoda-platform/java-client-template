package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EnrichPetProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        EnrichPetProcessor processor = new EnrichPetProcessor(serializerFactory);

        // Build a valid Pet entity that will pass isValid()
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID().toString());
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("PERSISTED"); // should be promoted to ENRICHED
        pet.setBio("A very playful and friendly dog who loves to cuddle.");
        pet.setHealthNotes(null); // should be defaulted
        // photos contain trimmed urls (with leading/trailing spaces) - still non-blank so valid
        pet.setPhotos(List.of(" http://example.com/photo1.jpg ", "http://example.com/photo2.jpg"));
        // tags left null to trigger inference
        pet.setTags(null);

        // Convert to Json payload
        var entityJson = objectMapper.valueToTree(pet);
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(pet.getId());
        request.setProcessorName(EnrichPetProcessor.class.getSimpleName());
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

        // Inspect returned payload and map back to Pet
        assertNotNull(response.getPayload());
        var outData = response.getPayload().getData();
        assertNotNull(outData);

        Pet outPet;
        try {
            outPet = objectMapper.treeToValue(outData, Pet.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize response payload to Pet", ex);
        }

        // Core assertions for sunny-day enrichments
        // 1) tags inferred (should include 'playful' and species 'dog')
        assertNotNull(outPet.getTags());
        assertTrue(outPet.getTags().stream().anyMatch(t -> t.equalsIgnoreCase("playful")));
        assertTrue(outPet.getTags().stream().anyMatch(t -> t.equalsIgnoreCase("dog")));

        // 2) healthNotes defaulted when missing
        assertNotNull(outPet.getHealthNotes());
        assertFalse(outPet.getHealthNotes().isBlank());
        assertEquals("Health information not provided", outPet.getHealthNotes());

        // 3) photos trimmed and preserved
        assertNotNull(outPet.getPhotos());
        assertTrue(outPet.getPhotos().contains("http://example.com/photo1.jpg"));
        assertTrue(outPet.getPhotos().contains("http://example.com/photo2.jpg"));

        // 4) status promoted to ENRICHED
        assertEquals("ENRICHED", outPet.getStatus());

        // 5) importedAt set
        assertNotNull(outPet.getImportedAt());
        assertFalse(outPet.getImportedAt().isBlank());
    }
}