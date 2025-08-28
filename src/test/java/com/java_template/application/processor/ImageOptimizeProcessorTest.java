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
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class ImageOptimizeProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real serializers & factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        ImageOptimizeProcessor processor = new ImageOptimizeProcessor(serializerFactory);

        // Build a valid Pet entity that passes isValid()
        Pet pet = new Pet();
        pet.setId("pet-123");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");
        List<String> photos = new ArrayList<>();
        photos.add("http://example.com/image1.jpg"); // no query
        photos.add("http://example.com/image2.jpg?size=large"); // has query
        pet.setPhotos(photos);
        // no tags set to exercise tag addition

        JsonNode entityJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(pet.getId());
        request.setProcessorName("ImageOptimizeProcessor");
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
        assertNotNull(response, "Response must not be null");
        assertTrue(response.getSuccess(), "Processing should succeed on sunny path");

        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Response payload data must not be null");

        // status should be set to IMAGES_READY
        assertEquals("IMAGES_READY", out.get("status").asText());

        // photos should be present and optimized
        JsonNode photosNode = out.get("photos");
        assertNotNull(photosNode);
        assertTrue(photosNode.isArray());
        assertEquals(2, photosNode.size());
        for (JsonNode pNode : photosNode) {
            String url = pNode.asText();
            assertTrue(url.contains("optimized=true"), "Each photo url should contain optimized=true");
        }

        // tags should include images_optimized
        JsonNode tagsNode = out.get("tags");
        assertNotNull(tagsNode);
        assertTrue(tagsNode.isArray());
        boolean found = false;
        for (JsonNode t : tagsNode) {
            if ("images_optimized".equalsIgnoreCase(t.asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Tags must contain images_optimized");
    }
}