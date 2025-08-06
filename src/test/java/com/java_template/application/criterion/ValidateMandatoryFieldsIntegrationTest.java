package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ValidateMandatoryFields using real serializer implementation
 * to test the complete validation flow without mocks.
 */
class ValidateMandatoryFieldsIntegrationTest {

    private ObjectMapper objectMapper;
    private ValidateMandatoryFields criterion;
    private EntityCriteriaCalculationRequest request;
    private CyodaEventContext<EntityCriteriaCalculationRequest> eventContext;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SerializerFactory serializerFactory = new SerializerFactory(
            List.of(),
            List.of(new com.java_template.common.serializer.jackson.JacksonCriterionSerializer(objectMapper))
        );
        criterion = new ValidateMandatoryFields(serializerFactory);

        // Create test request
        request = new EntityCriteriaCalculationRequest();
        request.setId("test-criteria-123");
        request.setRequestId("req-456");
        request.setEntityId("entity-789");
        request.setCriteriaId("criterion-123");
        request.setCriteriaName("ValidateMandatoryFields");
    }

    private void setupPayload(ObjectNode payload) {
        DataPayload dataPayload = new DataPayload();
        dataPayload.setData(payload);
        request.setPayload(dataPayload);
        eventContext = new CyodaEventContext<>() {
            @Override
            public CloudEvent getCloudEvent() {
                return mock(CloudEvent.class);
            }

            @Override
            public @NotNull EntityCriteriaCalculationRequest getEvent() {
                return request;
            }
        };
    }

    @Test
    void check_ValidEntity_ReturnsSuccess() {
        // Arrange - create valid HackerNewsItem payload
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);
        payload.put("type", "story");

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("id", 8863L);
        itemData.put("type", "story");
        itemData.put("by", "dhouston");
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getMatches());
    }

    @Test
    void check_MissingId_ReturnsFail() {
        // Arrange - payload missing id
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "story");

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("type", "story");
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess()); // Criterion executed successfully
        assertFalse(response.getMatches()); // But validation failed
    }

    @Test
    void check_MissingType_ReturnsFail() {
        // Arrange - payload missing type
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("id", 8863L);
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());
    }

    @Test
    void check_BlankType_ReturnsFail() {
        // Arrange - payload with blank type
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);
        payload.put("type", "   ");

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("id", 8863L);
        itemData.put("type", "   ");
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());
    }

    @Test
    void check_MissingItemData_ReturnsFail() {
        // Arrange - payload missing item data
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);
        payload.put("type", "story");
        // No item field

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());
    }

    @Test
    void check_ItemMissingId_ReturnsFail() {
        // Arrange - item data missing id
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);
        payload.put("type", "story");

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("type", "story");
        // Missing id in item
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());
    }

    @Test
    void check_ItemMissingType_ReturnsFail() {
        // Arrange - item data missing type
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);
        payload.put("type", "story");

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("id", 8863L);
        // Missing type in item
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());
    }

    @Test
    void supports_CorrectCriterionName_ReturnsTrue() {
        // Arrange
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("HackerNewsItem");
        modelSpec.setVersion(1000);
        OperationSpecification operationSpec = new OperationSpecification.Entity(modelSpec, "ValidateMandatoryFields");

        // Act
        boolean result = criterion.supports(operationSpec);

        // Assert
        assertTrue(result);
    }

    @Test
    void supports_IncorrectCriterionName_ReturnsFalse() {
        // Arrange
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("HackerNewsItem");
        modelSpec.setVersion(1000);
        OperationSpecification operationSpec = new OperationSpecification.Entity(modelSpec, "DifferentCriterion");

        // Act
        boolean result = criterion.supports(operationSpec);

        // Assert
        assertFalse(result);
    }

    @Test
    void check_ComplexValidEntity_ReturnsSuccess() {
        // Arrange - create complex valid payload with additional fields
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);
        payload.put("type", "story");
        payload.put("importTimestamp", "2024-04-27T15:30:00Z");

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("id", 8863L);
        itemData.put("type", "story");
        itemData.put("by", "dhouston");
        itemData.put("time", 1175714200);
        itemData.put("text", "Example story text");
        itemData.put("url", "https://example.com");
        itemData.put("score", 100);
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getMatches());
    }

    @Test
    void check_NullItemData_ReturnsFail() {
        // Arrange - payload with null item
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L);
        payload.put("type", "story");
        payload.putNull("item");

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());
    }
}
