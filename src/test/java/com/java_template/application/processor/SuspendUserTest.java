package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.audit.version_1.Audit;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SuspendUserTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Construct processor with real serializer factory and mocked EntityService
        SuspendUser processor = new SuspendUser(serializerFactory, entityService, objectMapper);

        // Prepare a valid User entity (must satisfy isValid())
        User user = new User();
        user.setUserId("user-123");
        user.setEmail("user@example.com");
        user.setGdprState("active");
        user.setMarketingEnabled(Boolean.TRUE);
        // auditRefs left null to test processor initializes it

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(user);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("SuspendUser");
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

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload data for expected sunny-day changes
        assertNotNull(response.getPayload());
        JsonNode data = response.getPayload().getData();
        assertNotNull(data);

        // gdprState should be set to "suspended"
        assertTrue(data.has("gdprState"));
        assertEquals("suspended", data.get("gdprState").asText());

        // marketingEnabled should be false
        assertTrue(data.has("marketingEnabled"));
        assertFalse(data.get("marketingEnabled").asBoolean());

        // auditRefs should contain the new audit id
        assertTrue(data.has("auditRefs"));
        JsonNode auditRefs = data.get("auditRefs");
        assertTrue(auditRefs.isArray());
        assertEquals(1, auditRefs.size());
        assertFalse(auditRefs.get(0).asText().isBlank());

        // Verify EntityService.addItem was called to persist Audit
        verify(entityService, atLeastOnce()).addItem(eq(Audit.ENTITY_NAME), eq(Audit.ENTITY_VERSION), any(Audit.class));
    }
}