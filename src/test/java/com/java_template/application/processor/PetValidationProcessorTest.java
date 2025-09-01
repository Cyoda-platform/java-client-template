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
        // Setup ObjectMapper as specified
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Instantiate processor (no mocks required for this processor)
        PetValidationProcessor processor = new PetValidationProcessor(serializerFactory);

        // Build a valid Pet entity that passes isValid() and should result in status -> VALIDATED
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID().toString());
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("NEW"); // non-terminal status, processor should set to VALIDATED
        pet.setAge(3);
        pet.setPhotoUrls(List.of("http://example.com/photo.jpg"));
        pet.setVaccinations(List.of("rabies"));

        JsonNode entityJson = objectMapper.valueToTree(pet);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PetValidationProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData, "Response payload data should be present");
        assertEquals("VALIDATED", respData.get("status").asText(), "Pet status should be set to VALIDATED in sunny path");
    }
}