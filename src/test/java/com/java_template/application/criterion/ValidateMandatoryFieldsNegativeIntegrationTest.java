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
 * Integration test for ValidateMandatoryFieldsNegative using real serializer implementation
 * to test the complete negative validation flow without mocks.
 */
class ValidateMandatoryFieldsNegativeIntegrationTest {

    private ObjectMapper objectMapper;
    private ValidateMandatoryFieldsNegative criterion;
    private EntityCriteriaCalculationRequest request;
    private CyodaEventContext<EntityCriteriaCalculationRequest> eventContext;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SerializerFactory serializerFactory = new SerializerFactory(
            List.of(),
            List.of(new com.java_template.common.serializer.jackson.JacksonCriterionSerializer(objectMapper))
        );
        criterion = new ValidateMandatoryFieldsNegative(serializerFactory);

        // Create test request
        request = new EntityCriteriaCalculationRequest();
        request.setId("test-criteria-123");
        request.setRequestId("req-456");
        request.setEntityId("entity-789");
        request.setCriteriaId("criterion-123");
        request.setCriteriaName("ValidateMandatoryFieldsNegative");
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
    void check_CompletelyValidEntity_ReturnsFail() {
        // Arrange - create completely valid HackerNewsItem payload
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
        assertTrue(response.getSuccess()); // Criterion executed successfully
        assertFalse(response.getMatches()); // But negative validation failed (entity is valid)
    }

    @Test
    void check_MissingId_ReturnsSuccess() {
        // Arrange - payload missing id (invalid entity)
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
        assertTrue(response.getSuccess());
        assertTrue(response.getMatches()); // Negative validation succeeds for invalid entity
    }

    @Test
    void check_MissingType_ReturnsSuccess() {
        // Arrange - payload missing type (invalid entity)
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
        assertTrue(response.getMatches());
    }

    @Test
    void check_BlankType_ReturnsSuccess() {
        // Arrange - payload with blank type (invalid entity)
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
        assertTrue(response.getMatches());
    }

    @Test
    void check_MissingItemData_ReturnsSuccess() {
        // Arrange - payload missing item data (invalid entity)
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
        assertTrue(response.getMatches());
    }

    @Test
    void check_ItemMissingId_ReturnsSuccess() {
        // Arrange - item data missing id (invalid entity)
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
        assertTrue(response.getMatches());
    }

    @Test
    void check_ItemMissingType_ReturnsSuccess() {
        // Arrange - item data missing type (invalid entity)
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
        assertTrue(response.getMatches());
    }

    @Test
    void supports_CorrectCriterionName_ReturnsTrue() {
        // Arrange
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("HackerNewsItem");
        modelSpec.setVersion(1000);
        OperationSpecification operationSpec = new OperationSpecification.Entity(modelSpec, "ValidateMandatoryFieldsNegative");

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
    void check_MultipleInvalidFields_ReturnsSuccess() {
        // Arrange - multiple validation failures (invalid entity)
        ObjectNode payload = objectMapper.createObjectNode();
        // Missing id and type
        payload.putNull("item");

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getMatches()); // Negative validation succeeds for invalid entity
    }

    @Test
    void check_PartiallyValidEntity_ReturnsSuccess() {
        // Arrange - some fields valid, some invalid
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 8863L); // valid
        // Missing type - invalid

        ObjectNode itemData = objectMapper.createObjectNode();
        itemData.put("id", 8863L);
        itemData.put("type", "story");
        payload.set("item", itemData);

        setupPayload(payload);

        // Act
        EntityCriteriaCalculationResponse response = criterion.check(eventContext);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getMatches()); // Negative validation succeeds for partially invalid entity
    }

    @Test
    void check_NullItemData_ReturnsSuccess() {
        // Arrange - payload with null item (invalid entity)
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
        assertTrue(response.getMatches());
    }
}
