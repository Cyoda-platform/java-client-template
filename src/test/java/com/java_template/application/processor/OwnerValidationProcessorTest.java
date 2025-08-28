package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.owner.version_1.Owner;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class OwnerValidationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked per requirements
        EntityService entityService = mock(EntityService.class);

        OwnerValidationProcessor processor = new OwnerValidationProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid Owner entity that will pass isValid() and trigger the "staff" auto-verify path.
        Owner owner = new Owner();
        owner.setOwnerId("owner-123");
        owner.setName("Alice Example");
        owner.setContactEmail("  alice@example.com  "); // will be trimmed by processor
        owner.setContactPhone("  +1-800-555-1234  "); // will be trimmed by processor
        owner.setRole("staff"); // should auto-verify
        // initial verification status can be anything (null or pending)
        owner.setVerificationStatus("pending");

        JsonNode ownerJson = objectMapper.valueToTree(owner);

        DataPayload payload = new DataPayload();
        payload.setData(ownerJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("owner-123");
        request.setProcessorName("OwnerValidationProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should succeed on sunny-day input");

        assertNotNull(response.getPayload(), "Response should contain a payload");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Payload data should not be null");

        // Verify that email and phone were trimmed and verificationStatus set to "verified"
        assertEquals("alice@example.com", out.path("contactEmail").asText());
        assertEquals("+1-800-555-1234", out.path("contactPhone").asText());
        assertEquals("verified", out.path("verificationStatus").asText());
        // basic sanity: ownerId and name preserved
        assertEquals("owner-123", out.path("ownerId").asText());
        assertEquals("Alice Example", out.path("name").asText());
    }
}