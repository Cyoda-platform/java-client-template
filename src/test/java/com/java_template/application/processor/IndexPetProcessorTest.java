package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IndexPetProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and SerializerFactory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        IndexPetProcessor processor = new IndexPetProcessor(serializerFactory);

        // Prepare a valid Pet entity that should be transformed from AVAILABLE -> LISTED
        Pet pet = new Pet();
        pet.setId("pet-123");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");
        Pet.Metadata metadata = new Pet.Metadata();
        metadata.setImages(new ArrayList<>()); // must be non-null if metadata present
        metadata.setTags(new ArrayList<>());   // must be non-null if metadata present
        metadata.setEnrichedAt(null);          // allowed to be null so processor will set it
        pet.setMetadata(metadata);

        JsonNode entityJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("IndexPetProcessor");
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

        // Assert - basic sunny-path assertions
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        DataPayload respPayload = response.getPayload();
        assertNotNull(respPayload, "Response payload should not be null");

        JsonNode data = respPayload.getData();
        assertNotNull(data, "Response data should not be null");

        // Status should be changed to LISTED
        assertEquals("LISTED", data.get("status").asText(), "Pet should be moved to LISTED");

        // Metadata should contain the "indexed" tag
        JsonNode metadataNode = data.get("metadata");
        assertNotNull(metadataNode, "Metadata should be present");
        JsonNode tagsNode = metadataNode.get("tags");
        assertTrue(tagsNode.isArray(), "Tags should be an array");

        boolean hasIndexed = false;
        for (JsonNode t : tagsNode) {
            if ("indexed".equalsIgnoreCase(t.asText())) {
                hasIndexed = true;
                break;
            }
        }
        assertTrue(hasIndexed, "Metadata tags should include 'indexed'");

        // EnrichedAt should be set (non-blank)
        JsonNode enrichedAtNode = metadataNode.get("enrichedAt");
        assertNotNull(enrichedAtNode, "enrichedAt should be set");
        assertFalse(enrichedAtNode.asText().isBlank(), "enrichedAt should be a non-blank timestamp");
    }
}