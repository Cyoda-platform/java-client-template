package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DetermineAvailabilityProcessorTest {

    @Test
    void sunnyDay_pet_enriched_becomes_available() {
        // Arrange - ObjectMapper and serializers (real, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        DetermineAvailabilityProcessor processor = new DetermineAvailabilityProcessor(serializerFactory);

        // Build a valid Pet entity that represents an enriched pet (enrichedAt present and has images)
        Pet pet = new Pet();
        pet.setId("pet-1");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("NEW"); // initial status (non-blank to satisfy isValid)
        Pet.Metadata metadata = new Pet.Metadata();
        metadata.setEnrichedAt("2020-01-01T00:00:00Z");
        metadata.setImages(List.of("http://example.com/image1.jpg"));
        metadata.setTags(List.of()); // non-null list
        pet.setMetadata(metadata);

        JsonNode entityJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("pet-1");
        request.setProcessorName("DetermineAvailabilityProcessor");
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

        // Assert - sunny day: response success and status becomes AVAILABLE
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);
        assertEquals("AVAILABLE", returnedData.get("status").asText());
        assertEquals("pet-1", returnedData.get("id").asText());
    }
}