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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OwnerValidationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and serializers (real Jackson objects)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Under test
        OwnerValidationProcessor processor = new OwnerValidationProcessor(serializerFactory);

        // Build a valid Owner entity that should pass isValid() and be promoted to "owner"
        Owner owner = new Owner();
        owner.setId("OWN-1");
        owner.setFullName("Jane Doe");
        owner.setEmail("jane.doe@example.com");
        owner.setPhone("+12345678901");
        owner.setAddress("123 Main St");
        owner.setBio("Loves pets");
        owner.setRole("visitor"); // should be promoted to "owner" in sunny path
        owner.setFavoritePetIds(null);
        owner.setCreatedAt(Instant.now());
        owner.setUpdatedAt(null);

        // Convert to JsonNode payload using ObjectMapper (use the objectMapper instance as required)
        JsonNode ownerJson = objectMapper.valueToTree(owner);

        DataPayload payload = new DataPayload();
        payload.setData(ownerJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("OwnerValidationProcessor");
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
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Verify the sunny-day behavior: role promoted to "owner" and updatedAt set (non-null)
        assertEquals("owner", out.path("role").asText());
        assertFalse(out.path("updatedAt").isMissingNode());
        assertFalse(out.path("updatedAt").isNull());
    }
}