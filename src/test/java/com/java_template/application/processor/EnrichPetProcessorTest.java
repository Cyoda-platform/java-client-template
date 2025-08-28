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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class EnrichPetProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService may be mocked (constructor requires it)
        EntityService entityService = mock(EntityService.class);

        EnrichPetProcessor processor = new EnrichPetProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid Pet entity that will pass isValid()
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID().toString());
        pet.setName("  Fido  ");
        pet.setSpecies(" Dog ");
        pet.setStatus("PERSISTED"); // will be normalized to AVAILABLE by processor
        pet.setBreed("Labrador ");
        pet.setAgeMonths(24);
        // Leave metadata null to exercise initialization behavior in processor

        JsonNode entityJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("EnrichPetProcessor");
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

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload for expected sunny-day enrichments
        assertNotNull(response.getPayload());
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData);

        // Status should be set to AVAILABLE
        assertEquals("AVAILABLE", outData.path("status").asText());

        // Species should be trimmed and lowercased
        assertEquals("dog", outData.path("species").asText());

        // Breed should be trimmed
        assertEquals("Labrador", outData.path("breed").asText());

        // Metadata should be present with enrichedAt and initialized lists
        JsonNode metadata = outData.path("metadata");
        assertTrue(metadata.isObject());
        String enrichedAt = metadata.path("enrichedAt").asText();
        assertNotNull(enrichedAt);
        assertFalse(enrichedAt.isBlank());

        JsonNode images = metadata.path("images");
        assertTrue(images.isArray());
        assertEquals(0, images.size());

        JsonNode tags = metadata.path("tags");
        assertTrue(tags.isArray());
        // tags should contain species and breed in lowercase
        List<String> tagStrings = java.util.stream.Stream.iterate(0, i -> i + 1)
                .limit(tags.size())
                .map(i -> tags.get(i).asText())
                .toList();
        assertTrue(tagStrings.contains("dog"));
        assertTrue(tagStrings.contains("labrador"));
    }
}