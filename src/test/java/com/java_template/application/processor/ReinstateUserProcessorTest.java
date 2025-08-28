package com.java_template.application.processor;

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

public class ReinstateUserProcessorTest {

    @Test
    void sunnyDay_reinstateUser_process_test() {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per instructions
        EntityService entityService = mock(EntityService.class);

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        ReinstateUserProcessor processor = new ReinstateUserProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid User entity JSON that is Suspended and should be reinstated to Active
        User user = new User();
        user.setUserId("user-123");
        user.setFullName("Jane Doe");
        user.setEmail("jane.doe@example.com");
        user.setRegisteredAt("2025-01-01T00:00:00Z");
        user.setStatus("Suspended"); // initial suspended status -> processor should set to Active

        JsonNode userJson = objectMapper.valueToTree(user);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ReinstateUserProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(userJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload data for status change to Active
        assertNotNull(response.getPayload());
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData);
        assertEquals("Active", responseData.get("status").asText());
        // Ensure userId preserved
        assertEquals("user-123", responseData.get("userId").asText());
    }
}