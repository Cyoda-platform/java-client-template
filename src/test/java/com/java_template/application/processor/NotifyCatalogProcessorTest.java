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
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class NotifyCatalogProcessorTest {

    @Test
    void sunnyDay_notifyCatalog_marksPetNotifiedAndNormalizesFields() {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per rules (processor constructor requires it)
        EntityService entityService = mock(EntityService.class);

        NotifyCatalogProcessor processor = new NotifyCatalogProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Pet entity that represents the sunny-day input
        Pet pet = new Pet();
        pet.setId("pet-1");
        pet.setName("Fido");
        pet.setSpecies("  Dog  ");    // will be trimmed and lowercased to "dog"
        pet.setBreed("  Labrador ");  // will be trimmed to "Labrador"
        pet.setStatus("AVAILABLE");   // triggers the notification branch
        pet.setPhotoUrls(null);       // will be initialized to empty list by processor

        JsonNode petJson = objectMapper.valueToTree(pet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifyCatalogProcessor");
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

        // Assert - basic sunny-day expectations
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data must be present");

        // Status should be set to NOTIFIED
        assertTrue(responseData.has("status"), "status field should be present");
        assertEquals("NOTIFIED", responseData.get("status").asText(), "Pet status should be NOTIFIED");

        // Species normalized to lowercase trimmed value
        assertTrue(responseData.has("species"), "species field should be present");
        assertEquals("dog", responseData.get("species").asText(), "Species should be normalized to 'dog'");

        // Breed trimmed
        assertTrue(responseData.has("breed"), "breed field should be present");
        assertEquals("Labrador", responseData.get("breed").asText(), "Breed should be trimmed");

        // photoUrls should exist and be an empty array (initialized)
        assertTrue(responseData.has("photoUrls"), "photoUrls should be present");
        JsonNode photoUrls = responseData.get("photoUrls");
        assertTrue(photoUrls.isArray(), "photoUrls must be an array");
        assertEquals(0, photoUrls.size(), "photoUrls should be initialized to empty list");
    }
}