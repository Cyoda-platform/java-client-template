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

public class EnrichPetProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        EnrichPetProcessor processor = new EnrichPetProcessor(serializerFactory);

        // Create a valid Pet entity that passes isValid()
        Pet pet = new Pet();
        pet.setName("Buddy");
        pet.setPetId("pet-123");
        pet.setSpecies("Cat");
        pet.setStatus("PERSISTED"); // will be transitioned to "Available" by processor
        pet.setBreed("Siamese");
        pet.setAge(3);
        // leave createdAt null to trigger enrichment; lists and metadata are initialized by default

        JsonNode petJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("EnrichPetProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData);

        // Status should be transitioned to "Available"
        assertEquals("Available", resultData.get("status").asText());

        // createdAt should be set by enrichment
        assertTrue(resultData.hasNonNull("createdAt"));
        assertFalse(resultData.get("createdAt").asText().isBlank());

        // images should contain a default species-based image (species "Cat" -> "cat.png")
        JsonNode images = resultData.get("images");
        assertTrue(images.isArray());
        assertTrue(images.size() > 0);
        String firstImage = images.get(0).asText();
        assertTrue(firstImage.contains("cat.png") || firstImage.toLowerCase().contains("cat"));

        // metadata should contain enrichment markers and breedInfo
        JsonNode metadata = resultData.get("metadata");
        assertNotNull(metadata);
        assertTrue(metadata.get("enriched").asBoolean());
        assertTrue(metadata.hasNonNull("enrichedAt"));
        assertEquals("Siamese", metadata.get("breedInfo").asText());

        // tags should include species, breed and age-group ("adult" for age 3)
        JsonNode tags = metadata.get("tags");
        assertTrue(tags.isArray());
        boolean hasSpecies = false, hasBreed = false, hasAgeGroup = false;
        for (JsonNode t : tags) {
            String v = t.asText();
            if ("Cat".equals(v)) hasSpecies = true;
            if ("Siamese".equals(v)) hasBreed = true;
            if ("adult".equals(v)) hasAgeGroup = true;
        }
        assertTrue(hasSpecies);
        assertTrue(hasBreed);
        assertTrue(hasAgeGroup);
    }
}