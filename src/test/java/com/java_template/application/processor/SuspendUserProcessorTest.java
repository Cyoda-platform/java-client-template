package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
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

public class SuspendUserProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked (required by processor constructor)
        EntityService entityService = mock(EntityService.class);

        // Instantiate processor directly (no Spring)
        SuspendUserProcessor processor = new SuspendUserProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid User entity that passes isValid()
        User user = new User();
        user.setUserId("user-123");
        user.setFullName("Jane Doe");
        user.setEmail("jane.doe@example.com");
        user.setRegisteredAt("2020-01-01T00:00:00Z");
        user.setStatus("Active"); // Eligible to be suspended

        JsonNode entityJson = objectMapper.valueToTree(user);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("SuspendUserProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode dataNode = response.getPayload().getData();
        assertNotNull(dataNode);

        // Core sunny-day assertions: status updated to "Suspended" and preferences contain suspension info
        assertEquals("Suspended", dataNode.get("status").asText(), "User status should be set to Suspended");

        JsonNode prefs = dataNode.get("preferences");
        assertNotNull(prefs, "preferences should be present");
        assertTrue(prefs.has("suspendedAt"), "preferences should contain suspendedAt");
        assertTrue(prefs.get("suspendedAt").asText().length() > 0, "suspendedAt should be non-empty");

        assertTrue(prefs.has("suspensionMethod"), "preferences should contain suspensionMethod");
        assertEquals("manual", prefs.get("suspensionMethod").asText(), "suspensionMethod should be 'manual'");
    }
}