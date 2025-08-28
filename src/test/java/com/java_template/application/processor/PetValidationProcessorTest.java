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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PetValidationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange: configure ObjectMapper (ignore unknown properties)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService required)
        PetValidationProcessor processor = new PetValidationProcessor(serializerFactory);

        // Build a valid Pet entity that will pass isValid() and exercise the sunny path.
        // Use status "PERSISTED_BY_PROCESS" so the processor will set it to "VALIDATED".
        Pet pet = new Pet();
        pet.setPetId("pet-123");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("PERSISTED_BY_PROCESS");
        pet.setPhotoUrls(List.of("http://example.com/photo1.jpg"));
        pet.setTags(List.of("friendly"));

        JsonNode petJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("pet-123");
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

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        // Verify that the processor updated the status from PERSISTED_BY_PROCESS -> VALIDATED
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode resultingData = response.getPayload().getData();
        assertNotNull(resultingData, "Resulting data must not be null");
        assertEquals("VALIDATED", resultingData.get("status").asText(), "Status should be set to VALIDATED");
    }
}