package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class TrustLevelProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real ObjectMapper configured to ignore unknown properties
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of((CriterionSerializer) criterionSerializer)
        );

        // Only EntityService is mocked because constructor requires it
        EntityService entityService = mock(EntityService.class);

        // Construct processor with real serializerFactory, mocked entityService, real objectMapper
        TrustLevelProcessor processor = new TrustLevelProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid User entity that meets trust criteria:
        // - status "Active"
        // - registeredAt older than 180 days
        // - adoptedPetIds contains at least one non-blank id
        User user = new User();
        user.setUserId("user-123");
        user.setFullName("Sunny Day");
        user.setEmail("sunny@example.com");
        // registeredAt older than 180 days
        user.setRegisteredAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(200).toString());
        user.setStatus("Active");
        user.setAdoptedPetIds(List.of("pet-1"));

        // Convert entity to JsonNode payload (serializer will convert back to entity)
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(user));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("TrustLevelProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic success and that status was elevated to "Trusted"
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor should indicate success");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        assertNotNull(response.getPayload().getData(), "Response payload data should not be null");

        String resultingStatus = response.getPayload().getData().get("status").asText();
        assertEquals("Trusted", resultingStatus, "User status should be promoted to Trusted on sunny-path");
    }
}