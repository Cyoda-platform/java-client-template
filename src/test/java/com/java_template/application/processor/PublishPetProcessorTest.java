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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PublishPetProcessorTest {

    @Test
    void sunnyDay_publish_pet_sets_available() {
        // Setup real ObjectMapper configured as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Instantiate processor (does not require EntityService)
        PublishPetProcessor processor = new PublishPetProcessor(serializerFactory);

        // Build a valid Pet entity that should be published (has images and healthy records)
        Pet pet = new Pet();
        pet.setName("Buddy");
        pet.setPetId("pet-123");
        pet.setSpecies("Dog");
        pet.setStatus("pending"); // non-terminal, non-available so processor will attempt publish
        pet.setAge(3);
        pet.getImages().add("http://example.com/image1.jpg"); // has images
        pet.getHealthRecords().add("vaccinated, healthy"); // healthy record
        // metadata left as default (non-null) and createdAt left null to exercise createdAt setting

        // Convert entity to JsonNode for payload
        JsonNode payloadData = objectMapper.valueToTree(pet);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PublishPetProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(payloadData);
        request.setPayload(payload);

        // Minimal CyodaEventContext anonymous implementation
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic success
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "processor should mark response as success");

        // Verify payload modifications: status should be set to "Available" and publishedBy/publishedAt/createdAt set
        assertNotNull(response.getPayload(), "response payload should not be null");
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData, "response payload data should not be null");

        // status -> Available (case as set by processor)
        assertTrue(outData.has("status"), "output entity should have status");
        assertEquals("Available", outData.get("status").asText(), "status should be set to Available");

        // metadata should contain publishedBy and publishedAt
        assertTrue(outData.has("metadata"), "output entity should have metadata");
        JsonNode metadata = outData.get("metadata");
        assertTrue(metadata.has("publishedBy"), "metadata should contain publishedBy");
        assertEquals("PublishPetProcessor", metadata.get("publishedBy").asText(), "publishedBy should be PublishPetProcessor");
        assertTrue(metadata.has("publishedAt"), "metadata should contain publishedAt");
        assertFalse(metadata.get("publishedAt").asText().isBlank(), "publishedAt should be non-blank");

        // createdAt should be set by processor (non-blank)
        assertTrue(outData.has("createdAt"), "output entity should have createdAt set");
        assertFalse(outData.get("createdAt").asText().isBlank(), "createdAt should be non-blank");
    }
}