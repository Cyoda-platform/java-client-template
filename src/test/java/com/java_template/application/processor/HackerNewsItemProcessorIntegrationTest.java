package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Integration test for HackerNewsItemProcessor using real serializer implementation
 * to test the complete processing flow without mocks.
 */
class HackerNewsItemProcessorIntegrationTest {

    private ObjectMapper objectMapper;
    private HackerNewsItemProcessor processor;
    private EntityProcessorCalculationRequest request;
    private ObjectNode testPayload;
    private CyodaEventContext<EntityProcessorCalculationRequest> eventContext;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SerializerFactory serializerFactory = new SerializerFactory(
            List.of(new com.java_template.common.serializer.jackson.JacksonProcessorSerializer(objectMapper)),
            List.of()
        );
        processor = new HackerNewsItemProcessor(serializerFactory, objectMapper);

        // Create test request with real data
        request = new EntityProcessorCalculationRequest();
        request.setId("test-request-123");
        request.setRequestId("req-456");
        request.setEntityId("entity-789");
        request.setProcessorId("processor-123");
        request.setProcessorName("HackerNewsItemProcessor");

        // Create test payload matching HackerNewsItem structure
        testPayload = objectMapper.createObjectNode();
        testPayload.put("id", 8863L);
        testPayload.put("type", "story");
        testPayload.put("by", "dhouston");
        testPayload.put("time", 1175714200);
        testPayload.put("text", "Example story text");

        // Create nested item structure
        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("id", 8863L);
        itemData.put("type", "story");
        itemData.put("by", "dhouston");
        itemData.put("time", 1175714200);
        itemData.put("text", "Example story text");
        testPayload.set("item", itemData);

        DataPayload payload = new DataPayload();
        payload.setData(testPayload);
        request.setPayload(payload);

        eventContext = new CyodaEventContext<>() {
            @Override
            public CloudEvent getCloudEvent() {
                return mock(CloudEvent.class);
            }

            @Override
            public @NotNull EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };
    }

    @Test
    void process_ValidEntity_AddsImportTimestamp() {
        // Record time before processing
        Instant beforeProcessing = Instant.now();

        // Act
        EntityProcessorCalculationResponse response = processor.process(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        // Verify the response contains the processed entity
        ObjectNode responseData = (ObjectNode) response.getPayload().getData();
        assertTrue(responseData.has("importTimestamp"));

        String importTimestamp = responseData.get("importTimestamp").asText();
        assertNotNull(importTimestamp);

        // Verify timestamp format is ISO 8601
        assertDoesNotThrow(() -> DateTimeFormatter.ISO_INSTANT.parse(importTimestamp));

        // Verify timestamp is recent (within last few seconds)
        Instant timestamp = Instant.parse(importTimestamp);
        Instant afterProcessing = Instant.now();
        assertTrue(timestamp.isAfter(beforeProcessing.minusSeconds(1)));
        assertTrue(timestamp.isBefore(afterProcessing.plusSeconds(1)));

        // Verify original data is preserved
        assertEquals(8863L, responseData.get("id").asLong());
        assertEquals("story", responseData.get("type").asText());

        // Verify item data also has importTimestamp
        assertTrue(responseData.has("item"));
        ObjectNode itemData = (ObjectNode) responseData.get("item");
        assertTrue(itemData.has("importTimestamp"));
        assertEquals(importTimestamp, itemData.get("importTimestamp").asText());
    }

    @Test
    void process_InvalidEntity_HandlesGracefully() {
        // Arrange - create invalid payload (missing required fields)
        ObjectNode invalidPayload = objectMapper.createObjectNode();
        invalidPayload.put("invalidField", "value");

        ObjectNode invalidItem = objectMapper.createObjectNode();
        invalidItem.put("invalidField", "value");
        invalidPayload.set("item", invalidItem);

        DataPayload payload = new DataPayload();
        payload.setData(invalidPayload);
        request.setPayload(payload);

        // Act & Assert - should handle gracefully without throwing
        assertDoesNotThrow(() -> {
            EntityProcessorCalculationResponse response = processor.process(eventContext);
            assertNotNull(response);
        });
    }

    @Test
    void supports_CorrectProcessorName_ReturnsTrue() {
        // Arrange
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("HackerNewsItem");
        modelSpec.setVersion(1000);
        OperationSpecification operationSpec = new OperationSpecification.Entity(modelSpec, "HackerNewsItemProcessor");

        // Act
        boolean result = processor.supports(operationSpec);

        // Assert
        assertTrue(result);
    }

    @Test
    void supports_IncorrectProcessorName_ReturnsFalse() {
        // Arrange
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("HackerNewsItem");
        modelSpec.setVersion(1000);
        OperationSpecification operationSpec = new OperationSpecification.Entity(modelSpec, "DifferentProcessor");

        // Act
        boolean result = processor.supports(operationSpec);

        // Assert
        assertFalse(result);
    }

    @Test
    void process_PreservesAllOriginalFields() {
        // Act
        EntityProcessorCalculationResponse response = processor.process(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        ObjectNode responseData = (ObjectNode) response.getPayload().getData();

        // Verify all original fields are preserved
        assertEquals(8863L, responseData.get("id").asLong());
        assertEquals("story", responseData.get("type").asText());
        assertEquals("dhouston", responseData.get("by").asText());
        assertEquals(1175714200, responseData.get("time").asLong());
        assertEquals("Example story text", responseData.get("text").asText());

        // Verify item structure is preserved
        assertTrue(responseData.has("item"));
        ObjectNode itemData = (ObjectNode) responseData.get("item");
        assertEquals(8863L, itemData.get("id").asLong());
        assertEquals("story", itemData.get("type").asText());
        assertEquals("dhouston", itemData.get("by").asText());
        assertEquals(1175714200, itemData.get("time").asLong());
        assertEquals("Example story text", itemData.get("text").asText());

        // Only importTimestamp should be added
        assertTrue(responseData.has("importTimestamp"));
        assertTrue(itemData.has("importTimestamp"));
    }

    @Test
    void process_NullItemData_HandlesGracefully() {
        // Arrange - payload without item data
        ObjectNode payloadWithoutItem = objectMapper.createObjectNode();
        payloadWithoutItem.put("id", 8863L);
        payloadWithoutItem.put("type", "story");

        DataPayload payload = new DataPayload();
        payload.setData(payloadWithoutItem);
        request.setPayload(payload);

        // Act & Assert - should handle gracefully
        assertDoesNotThrow(() -> {
            EntityProcessorCalculationResponse response = processor.process(eventContext);
            assertNotNull(response);

            // Should still add importTimestamp to main entity
            ObjectNode responseData = (ObjectNode) response.getPayload().getData();
            assertTrue(responseData.has("importTimestamp"));
        });
    }
}
