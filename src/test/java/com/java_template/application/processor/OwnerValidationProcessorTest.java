package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.owner.version_1.Owner;
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

public class OwnerValidationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - set up real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService required)
        OwnerValidationProcessor processor = new OwnerValidationProcessor(serializerFactory);

        // Create a valid Owner entity JSON that will pass isValid() and trigger PROFILE:COMPLETE and STATUS:ACTIVE
        Owner owner = new Owner();
        owner.setId("owner-1");
        owner.setName("John Doe");
        owner.setAddress("123 Main St");
        owner.setContactEmail(" John.Doe@Example.com "); // will be trimmed and lowercased by processor
        owner.setContactPhone(null);
        owner.setPreferences("existingPref1;existingPref2");

        JsonNode entityJson = objectMapper.valueToTree(owner);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("OwnerValidationProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should succeed in sunny-day path");
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned data should not be null");

        // contactEmail should be normalized to lowercase and trimmed
        assertEquals("john.doe@example.com", returnedData.get("contactEmail").asText());

        // id and name should be preserved
        assertEquals("owner-1", returnedData.get("id").asText());
        assertEquals("John Doe", returnedData.get("name").asText());

        // preferences should include preserved items and new PROFILE and STATUS tags
        String prefs = returnedData.get("preferences").asText();
        assertTrue(prefs.contains("existingPref1"), "preserved preference should remain");
        assertTrue(prefs.contains("existingPref2"), "preserved preference should remain");
        assertTrue(prefs.contains("PROFILE:COMPLETE"), "profile should be marked COMPLETE");
        assertTrue(prefs.contains("STATUS:ACTIVE"), "status should be marked ACTIVE");
    }
}