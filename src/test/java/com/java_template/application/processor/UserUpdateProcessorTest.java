package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
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

public class UserUpdateProcessorTest {

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

        UserUpdateProcessor processor = new UserUpdateProcessor(serializerFactory);

        // Build a valid User entity (use real entity, not raw JsonNode)
        User user = new User();
        user.setId("user-1");
        user.setName("  Alice  "); // intentional surrounding spaces to verify trimming
        user.setEmail("  alice@example.com  ");
        user.setPhone(" 123 ");
        // primaryAddress is optional; not required for validity here

        JsonNode entityJson = objectMapper.valueToTree(user);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("user-1");
        request.setProcessorName("UserUpdateProcessor");
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
        assertTrue(response.getSuccess());

        DataPayload respPayload = response.getPayload();
        assertNotNull(respPayload);
        JsonNode out = respPayload.getData();
        assertNotNull(out);

        // profileUpdatedAt should be set by the processor to a non-empty ISO timestamp
        assertTrue(out.hasNonNull("profileUpdatedAt"));
        String profileUpdatedAt = out.get("profileUpdatedAt").asText();
        assertNotNull(profileUpdatedAt);
        assertFalse(profileUpdatedAt.isBlank());

        // Name/email/phone should be trimmed by the processor
        assertEquals("Alice", out.get("name").asText());
        assertEquals("alice@example.com", out.get("email").asText());
        assertEquals("123", out.get("phone").asText());
    }
}