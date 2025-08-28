package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PetEnrichmentProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per requirements
        EntityService entityService = mock(EntityService.class);

        PetEnrichmentProcessor processor = new PetEnrichmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Pet entity that will pass isValid() and exercise core happy-path logic:
        // - age 36 should be normalized to 3 (months -> years heuristic)
        // - photos present and healthNotes null should result in healthNotes -> "Not specified"
        Pet pet = new Pet();
        pet.setTechnicalId("tech-1");
        pet.setId("external-1");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE"); // must be non-blank to pass validation
        pet.setAge(36); // months -> should be normalized to 3
        pet.setPhotos(List.of("http://example.com/photo1.jpg"));
        pet.setHealthNotes(null); // should be set to "Not specified" by processor

        JsonNode entityJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PetEnrichmentProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

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
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should succeed on sunny-path input");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Output data must be present in payload");

        // Age should be normalized from 36 (months) to 3 (years)
        assertTrue(out.has("age"), "Output should contain age");
        assertEquals(3, out.get("age").asInt(), "Age should be normalized from months to years (36 -> 3)");

        // Health notes should have been filled due to presence of photos
        assertTrue(out.has("healthNotes"), "Output should contain healthNotes");
        assertEquals("Not specified", out.get("healthNotes").asText(), "Missing healthNotes should be set to 'Not specified' when photos exist");

        // Ensure basic required fields preserved
        assertEquals("Fido", out.get("name").asText());
        assertEquals("Dog", out.get("species").asText());
        assertEquals("AVAILABLE", out.get("status").asText());
    }
}