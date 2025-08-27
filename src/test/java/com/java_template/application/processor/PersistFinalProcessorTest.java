package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PersistFinalProcessorTest {

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

        // Only EntityService is mocked per rules
        EntityService entityService = mock(EntityService.class);
        // Return empty array to simulate no existing laureates -> recordStatus should become "NEW"
        when(entityService.getItemsByCondition(anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(objectMapper.createArrayNode()));

        PersistFinalProcessor processor = new PersistFinalProcessor(serializerFactory, entityService, objectMapper);

        // Build payload JSON that passes initial validation (isValid requires persistedAt and recordStatus present)
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 123);
        data.put("firstname", "Marie");
        data.put("surname", "Curie");
        data.put("category", "Physics");
        data.put("year", "1903");
        data.put("persistedAt", "2020-01-01T00:00:00Z"); // required by isValid
        data.put("recordStatus", "OLD"); // will be overwritten to NEW by processor
        data.put("born", "1867-11-07"); // allow derivedAgeAtAward calculation
        // derivedAgeAtAward omitted so processor should compute it

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r-" + UUID.randomUUID());
        request.setRequestId("req-" + UUID.randomUUID());
        request.setEntityId("e-123");
        request.setProcessorName("PersistFinalProcessor");
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Expect the processor to have set recordStatus to NEW (no existing items)
        assertTrue(out.has("recordStatus"));
        assertEquals("NEW", out.get("recordStatus").asText());

        // Expect derivedAgeAtAward to be calculated: year 1903 - born year 1867 = 36
        assertTrue(out.has("derivedAgeAtAward"));
        assertEquals(36, out.get("derivedAgeAtAward").asInt());

        // persistedAt should remain present and non-blank
        assertTrue(out.has("persistedAt"));
        assertFalse(out.get("persistedAt").asText().isBlank());
    }
}