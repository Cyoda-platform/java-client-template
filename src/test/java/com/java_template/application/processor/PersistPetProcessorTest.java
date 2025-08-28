package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.junit.jupiter.api.Assertions.*;

public class PersistPetProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        PersistPetProcessor processor = new PersistPetProcessor(serializerFactory);

        // Build a minimal valid Pet entity that will pass validation (id, name, species, status required).
        Pet pet = new Pet();
        pet.setId("pet-1");
        pet.setName(" Fido "); // will be trimmed by processor
        pet.setSpecies("Dog");
        pet.setStatus("PERSISTED"); // processor normalizes to AVAILABLE
        pet.setBio("playful and friendly"); // used for tag inference
        // leave importedAt, source, sex, size, healthNotes, tags null so processor will set defaults

        JsonNode petJson = objectMapper.valueToTree(pet);

        DataPayload payload = new DataPayload();
        payload.setData(petJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(pet.getId());
        request.setProcessorName("PersistPetProcessor");
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
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Core sunny-path expectations: name trimmed, status normalized to AVAILABLE, source set, importedAt set
        assertEquals("Fido", out.path("name").asText());
        assertEquals("AVAILABLE", out.path("status").asText());
        assertEquals("Petstore", out.path("source").asText());
        String importedAt = out.path("importedAt").asText();
        assertNotNull(importedAt);
        assertFalse(importedAt.isBlank());

        // Defaults applied for sex and size and healthNotes and tags inferred
        assertEquals("unknown", out.path("sex").asText());
        assertEquals("medium", out.path("size").asText());
        assertEquals("No health notes provided", out.path("healthNotes").asText());

        // Tags should be present and contain at least "playful" and "friendly"
        JsonNode tagsNode = out.path("tags");
        assertTrue(tagsNode.isArray());
        boolean hasPlayful = false;
        boolean hasFriendly = false;
        for (JsonNode t : tagsNode) {
            String tag = t.asText();
            if ("playful".equals(tag)) hasPlayful = true;
            if ("friendly".equals(tag)) hasFriendly = true;
        }
        assertTrue(hasPlayful || hasFriendly); // at least one inferred tag should be present in sunny path
    }
}