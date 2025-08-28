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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EnrichmentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // Ignore unknown properties during deserialization as required
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService required)
        EnrichmentProcessor processor = new EnrichmentProcessor(serializerFactory);

        // Prepare a valid Pet entity that will pass validation before enrichment
        Pet inputPet = new Pet();
        inputPet.setId("pet-1");
        inputPet.setName("Fido");
        inputPet.setAge(3); // should map to "adult" bucket
        inputPet.setBreed("  golden   retriever "); // should normalize to "Golden Retriever"
        inputPet.setDescription(null); // processor should set default and append age bucket
        inputPet.setStatus("new"); // not ADOPTED/PENDING_ADOPTION so processor should set AVAILABLE

        // Convert to JsonNode for payload
        JsonNode entityJson = objectMapper.valueToTree(inputPet);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("pet-1");
        request.setProcessorName("EnrichmentProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Provide minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assertions
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should succeed in sunny-day path");

        // Extract resulting entity from response payload
        assertNotNull(response.getPayload(), "Response payload must be present");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Response payload data must be present");

        Pet resultPet = objectMapper.treeToValue(resultData, Pet.class);

        // Breed should be normalized to title case and collapsed spaces
        assertEquals("Golden Retriever", resultPet.getBreed(), "Breed should be normalized to title case with single spaces");

        // Description should have default text and age bucket appended
        assertNotNull(resultPet.getDescription(), "Description should be set by processor");
        assertTrue(resultPet.getDescription().contains("No description provided"), "Default description text should be applied");
        assertTrue(resultPet.getDescription().contains("Age group: adult"), "Age bucket should be appended to description");

        // Status should be set to AVAILABLE for unknown status
        assertEquals("AVAILABLE", resultPet.getStatus(), "Status should be set to AVAILABLE for non-adopted/non-pending statuses");

        // Age should remain unchanged
        assertEquals(3, resultPet.getAge());
    }
}