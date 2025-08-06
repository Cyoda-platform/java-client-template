package com.java_template.application.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.java_template.common.config.Config.ENTITY_VERSION;
import static org.junit.jupiter.api.Assertions.*;

class HackerNewsItemTest {

    private HackerNewsItem entity;
    private ObjectNode validItemData;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        validItemData = mapper.createObjectNode();
        validItemData.put("id", 8863);
        validItemData.put("type", "story");
        validItemData.put("by", "dhouston");
        validItemData.put("time", 1175714200);

        entity = new HackerNewsItem();
        entity.setId(8863L);
        entity.setType("story");
        entity.setItem(validItemData);
    }

    @Test
    void getModelKey_ReturnsCorrectSpecification() {
        // Act
        OperationSpecification modelKey = entity.getModelKey();

        // Assert
        assertNotNull(modelKey);
        assertInstanceOf(OperationSpecification.Entity.class, modelKey);

        ModelSpec modelSpec = modelKey.modelKey();
        assertEquals(HackerNewsItem.ENTITY_NAME, modelSpec.getName());
        assertEquals(Integer.parseInt(ENTITY_VERSION), modelSpec.getVersion());
        assertEquals(HackerNewsItem.ENTITY_NAME, modelKey.operationName());
    }

    @Test
    void isValid_ValidEntity_ReturnsTrue() {
        // Act
        boolean result = entity.isValid();

        // Assert
        assertTrue(result);
    }

    @Test
    void isValid_NullId_ReturnsFalse() {
        // Arrange
        entity.setId(null);

        // Act
        boolean result = entity.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_NullType_ReturnsFalse() {
        // Arrange
        entity.setType(null);

        // Act
        boolean result = entity.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_BlankType_ReturnsFalse() {
        // Arrange
        entity.setType("   ");

        // Act
        boolean result = entity.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_EmptyType_ReturnsFalse() {
        // Arrange
        entity.setType("");

        // Act
        boolean result = entity.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_NullItem_ReturnsFalse() {
        // Arrange
        entity.setItem(null);

        // Act
        boolean result = entity.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_MultipleInvalidFields_ReturnsFalse() {
        // Arrange
        entity.setId(null);
        entity.setType(null);
        entity.setItem(null);

        // Act
        boolean result = entity.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void settersAndGetters_WorkCorrectly() {
        // Arrange
        Long testId = 12345L;
        String testType = "comment";
        ObjectNode testItem = new ObjectMapper().createObjectNode();
        testItem.put("test", "value");
        String testTimestamp = "2024-04-27T15:30:00Z";

        // Act
        entity.setId(testId);
        entity.setType(testType);
        entity.setItem(testItem);
        entity.setImportTimestamp(testTimestamp);

        // Assert
        assertEquals(testId, entity.getId());
        assertEquals(testType, entity.getType());
        assertEquals(testItem, entity.getItem());
        assertEquals(testTimestamp, entity.getImportTimestamp());
    }

    @Test
    void entityName_IsCorrect() {
        // Assert
        assertEquals("HackerNewsItem", HackerNewsItem.ENTITY_NAME);
    }

    @Test
    void defaultConstructor_CreatesEmptyEntity() {
        // Act
        HackerNewsItem newEntity = new HackerNewsItem();

        // Assert
        assertNotNull(newEntity);
        assertNull(newEntity.getId());
        assertNull(newEntity.getType());
        assertNull(newEntity.getItem());
        assertNull(newEntity.getImportTimestamp());
    }

    @Test
    void isValid_WithValidTypeButWhitespace_ReturnsFalse() {
        // Arrange
        entity.setType("\t\n ");

        // Act
        boolean result = entity.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_WithMinimalValidData_ReturnsTrue() {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode minimalItem = mapper.createObjectNode();
        minimalItem.put("id", 1);
        minimalItem.put("type", "story");

        entity.setId(1L);
        entity.setType("story");
        entity.setItem(minimalItem);

        // Act
        boolean result = entity.isValid();

        // Assert
        assertTrue(result);
    }

    @Test
    void toString_ContainsAllFields() {
        // Arrange
        entity.setImportTimestamp("2024-04-27T15:30:00Z");

        // Act
        String result = entity.toString();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("8863"));
        assertTrue(result.contains("story"));
        assertTrue(result.contains("2024-04-27T15:30:00Z"));
    }

    @Test
    void equals_SameData_ReturnsTrue() {
        // Arrange
        HackerNewsItem other = new HackerNewsItem();
        other.setId(8863L);
        other.setType("story");
        other.setItem(validItemData);

        // Act & Assert
        assertEquals(entity, other);
        assertEquals(entity.hashCode(), other.hashCode());
    }

    @Test
    void equals_DifferentData_ReturnsFalse() {
        // Arrange
        HackerNewsItem other = new HackerNewsItem();
        other.setId(9999L);
        other.setType("comment");
        other.setItem(validItemData);

        // Act & Assert
        assertNotEquals(entity, other);
    }
}
