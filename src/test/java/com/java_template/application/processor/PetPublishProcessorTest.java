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

public class PetPublishProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        PetPublishProcessor processor = new PetPublishProcessor(serializerFactory);

        // Build a valid Pet that will be transformed by the processor:
        // - status is not AVAILABLE so processor will set it to AVAILABLE
        // - tags is non-null empty list so processor will add "listed"
        // - description is blank so processor will generate a friendly description
        Pet pet = new Pet();
        pet.setPetId("pet-123");
        pet.setName("Buddy");
        pet.setSpecies("Dog");
        pet.setStatus("PENDING");
        pet.setAge(3);
        pet.setPhotoUrls(new ArrayList<>()); // non-null, can be empty
        pet.setTags(new ArrayList<>()); // non-null, initially empty
        pet.setDescription(""); // blank to trigger generation

        JsonNode entityJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("pet-123");
        request.setProcessorName("PetPublishProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode data = response.getPayload().getData();
        assertNotNull(data);

        // status should be set to AVAILABLE
        assertEquals("AVAILABLE", data.get("status").asText());

        // tags should contain "listed"
        JsonNode tagsNode = data.get("tags");
        assertNotNull(tagsNode);
        boolean hasListed = false;
        for (JsonNode t : tagsNode) {
            if ("listed".equalsIgnoreCase(t.asText())) {
                hasListed = true;
                break;
            }
        }
        assertTrue(hasListed, "Expected tags to contain 'listed'");

        // description should be populated (non-blank)
        JsonNode descNode = data.get("description");
        assertNotNull(descNode);
        assertFalse(descNode.asText().isBlank(), "Expected description to be populated");
    }
}