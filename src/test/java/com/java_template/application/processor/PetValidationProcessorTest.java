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

public class PetValidationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper & serializers (real objects, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        PetValidationProcessor processor = new PetValidationProcessor(serializerFactory);

        // Prepare a minimal valid Pet entity that will trigger the "sunny day" logic
        Pet pet = new Pet();
        pet.setId("pet-1");
        pet.setName("Fido");
        pet.setAge(2); // >=1 => status should become AVAILABLE when initial status is CREATED
        pet.setStatus("CREATED"); // isValid requires non-blank status; business logic treats CREATED as initial state
        // leave breed, description, source null to exercise defaults

        JsonNode petJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("pet-1");
        request.setProcessorName("PetValidationProcessor");
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

        // Assert - basic success and payload checks
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should succeed");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Result data should not be null");

        // Core happy-path transformations:
        // - breed should be defaulted to "Unknown"
        // - description should be normalized to empty string
        // - source should default to "Petstore"
        // - status should become "AVAILABLE" for age >= 1 when initial status is CREATED
        assertEquals("Unknown", resultData.get("breed").asText(), "Breed should be set to 'Unknown'");
        assertEquals("", resultData.get("description").asText(), "Description should be normalized to empty string");
        assertEquals("Petstore", resultData.get("source").asText(), "Source should default to 'Petstore'");
        assertEquals("AVAILABLE", resultData.get("status").asText(), "Status should be set to AVAILABLE for age >= 1");
        // Ensure id and name preserved
        assertEquals("pet-1", resultData.get("id").asText(), "Id should be preserved");
        assertEquals("Fido", resultData.get("name").asText(), "Name should be preserved");
    }
}