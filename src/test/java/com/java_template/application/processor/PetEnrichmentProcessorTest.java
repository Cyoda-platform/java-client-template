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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PetEnrichmentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - configure real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked because processor constructor requires it
        EntityService entityService = mock(EntityService.class);

        PetEnrichmentProcessor processor = new PetEnrichmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Pet entity that passes isValid()
        Pet pet = new Pet();
        pet.setPetId("pet-123");
        pet.setName("Whiskers");
        pet.setSpecies("Cat");
        pet.setBreed("Siamese");
        pet.setAge(2); // should produce "juvenile" tag
        pet.setGender("Female");
        pet.setImportedFrom("ShelterX"); // should produce "imported" tag
        pet.setDescription(null); // processor should generate description
        pet.setPhotoUrls(List.of("http://example.com/photo.jpg"));
        pet.setStatus("AVAILABLE");
        pet.setTags(new ArrayList<>()); // must be non-null to pass isValid

        JsonNode petJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("pet-123");
        request.setProcessorName("PetEnrichmentProcessor");
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

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload for enriched tags and generated description
        assertNotNull(response.getPayload());
        JsonNode dataNode = response.getPayload().getData();
        assertNotNull(dataNode);

        JsonNode tagsNode = dataNode.get("tags");
        assertNotNull(tagsNode);
        assertTrue(tagsNode.isArray());

        List<String> tags = new ArrayList<>();
        tagsNode.forEach(n -> tags.add(n.asText()));

        // Expect species-based tags, age tag and imported tag to be present
        assertTrue(tags.contains("playful"), "Expected 'playful' tag");
        assertTrue(tags.contains("lapcat"), "Expected 'lapcat' tag");
        assertTrue(tags.contains("juvenile"), "Expected 'juvenile' tag");
        assertTrue(tags.contains("imported"), "Expected 'imported' tag");

        // Description should be generated and not blank
        JsonNode descNode = dataNode.get("description");
        assertNotNull(descNode);
        String desc = descNode.asText();
        assertNotNull(desc);
        assertFalse(desc.isBlank());
    }
}