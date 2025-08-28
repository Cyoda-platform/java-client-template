package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProfileCompletionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        ProfileCompletionProcessor processor = new ProfileCompletionProcessor(serializerFactory);

        // Create a valid Owner entity JSON payload (fields intentionally unnormalized)
        Owner owner = new Owner();
        owner.setId("owner-1");
        owner.setName("  John Doe  ");
        owner.setAddress(" 123 Main St  ");
        owner.setContactEmail(" JOHN.DOE@Example.COM  ");
        owner.setContactPhone(" +1 (234) 567-8900 ");
        owner.setPreferences(" likes-dogs ");

        JsonNode ownerJson = objectMapper.valueToTree(owner);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("owner-1");
        request.setProcessorName("ProfileCompletionProcessor");

        DataPayload payload = new DataPayload();
        payload.setData(ownerJson);
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
        assertTrue(response.getSuccess(), "Processing should succeed");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Result data must be present");

        // Deserialize result into Owner and verify normalization (sunny-day behavior)
        Owner resultOwner = objectMapper.treeToValue(resultData, Owner.class);
        assertNotNull(resultOwner);

        // Name and other string fields should be trimmed
        assertEquals("John Doe", resultOwner.getName(), "Name should be trimmed");
        assertEquals("123 Main St", resultOwner.getAddress(), "Address should be trimmed");
        assertEquals("likes-dogs", resultOwner.getPreferences(), "Preferences should be trimmed");

        // Email should be trimmed and lower-cased
        assertEquals("john.doe@example.com", resultOwner.getContactEmail(), "Email should be lower-cased and trimmed");

        // Phone should be normalized: digits-only preserving leading '+'
        assertEquals("+12345678900", resultOwner.getContactPhone(), "Phone should be normalized");
    }
}