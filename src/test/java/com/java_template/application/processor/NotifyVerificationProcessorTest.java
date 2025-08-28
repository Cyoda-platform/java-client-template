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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NotifyVerificationProcessorTest {

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

        NotifyVerificationProcessor processor = new NotifyVerificationProcessor(serializerFactory, objectMapper);

        // Build a valid Owner entity that will trigger the "verified" branch and set default role
        Owner owner = new Owner();
        owner.setOwnerId("owner-123");
        owner.setName("Jane Doe");
        owner.setContactEmail("jane.doe@example.com");
        owner.setVerificationStatus("verified");
        owner.setRole(null); // should be set to "user" by processor

        JsonNode ownerJson = objectMapper.valueToTree(owner);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId(owner.getOwnerId());
        request.setProcessorName("NotifyVerificationProcessor");
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
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "response should indicate success");

        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "output payload data should not be null");
        // Role should have been set to "user" when verificationStatus == "verified"
        assertEquals("user", out.path("role").asText(), "owner role should be set to 'user'");
        // Ensure contactEmail preserved
        assertEquals("jane.doe@example.com", out.path("contactEmail").asText(), "contactEmail should be preserved");
    }
}