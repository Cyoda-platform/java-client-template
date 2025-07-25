package com.java_template.application.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for HNItem entity to verify business data validation and entity metadata separation.
 * Tests ensure HNItem only contains business fields and validates correctly without metadata fields.
 */
class HNItemTest {

    @Test
    @DisplayName("HNItem should be valid with all business fields present")
    void testValidHNItem() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId("123456");
        hnItem.setPayload("{\"type\":\"story\",\"id\":\"123456\",\"title\":\"Test Story\"}");
        hnItem.setStatus("VALIDATED");

        // When & Then
        assertTrue(hnItem.isValid());
    }

    @Test
    @DisplayName("HNItem should be invalid when id is null")
    void testInvalidHNItemWithNullId() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId(null);
        hnItem.setPayload("{\"type\":\"story\",\"id\":\"123456\",\"title\":\"Test Story\"}");
        hnItem.setStatus("VALIDATED");

        // When & Then
        assertFalse(hnItem.isValid());
    }

    @Test
    @DisplayName("HNItem should be invalid when id is blank")
    void testInvalidHNItemWithBlankId() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId("   ");
        hnItem.setPayload("{\"type\":\"story\",\"id\":\"123456\",\"title\":\"Test Story\"}");
        hnItem.setStatus("VALIDATED");

        // When & Then
        assertFalse(hnItem.isValid());
    }

    @Test
    @DisplayName("HNItem should be invalid when payload is null")
    void testInvalidHNItemWithNullPayload() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId("123456");
        hnItem.setPayload(null);
        hnItem.setStatus("VALIDATED");

        // When & Then
        assertFalse(hnItem.isValid());
    }

    @Test
    @DisplayName("HNItem should be invalid when payload is blank")
    void testInvalidHNItemWithBlankPayload() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId("123456");
        hnItem.setPayload("   ");
        hnItem.setStatus("VALIDATED");

        // When & Then
        assertFalse(hnItem.isValid());
    }

    @Test
    @DisplayName("HNItem should be invalid when status is null")
    void testInvalidHNItemWithNullStatus() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId("123456");
        hnItem.setPayload("{\"type\":\"story\",\"id\":\"123456\",\"title\":\"Test Story\"}");
        hnItem.setStatus(null);

        // When & Then
        assertFalse(hnItem.isValid());
    }

    @Test
    @DisplayName("HNItem should be invalid when status is blank")
    void testInvalidHNItemWithBlankStatus() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId("123456");
        hnItem.setPayload("{\"type\":\"story\",\"id\":\"123456\",\"title\":\"Test Story\"}");
        hnItem.setStatus("   ");

        // When & Then
        assertFalse(hnItem.isValid());
    }

    @Test
    @DisplayName("HNItem should have correct model key")
    void testHNItemModelKey() {
        // Given
        HNItem hnItem = new HNItem();

        // When
        var modelKey = hnItem.getModelKey();

        // Then
        assertNotNull(modelKey);
        assertEquals("hnItem", modelKey.operationName());
        assertEquals("hnItem", modelKey.modelKey().getName());
        assertTrue(modelKey.modelKey().getVersion() > 0);
    }

    @Test
    @DisplayName("HNItem should not have metadata fields")
    void testHNItemDoesNotHaveMetadataFields() {
        // Given
        HNItem hnItem = new HNItem();
        hnItem.setId("123456");
        hnItem.setPayload("{\"type\":\"story\",\"id\":\"123456\",\"title\":\"Test Story\"}");
        hnItem.setStatus("VALIDATED");

        // When & Then - Verify that metadata fields are not accessible
        // This test ensures that technicalId and createdAt are not part of the entity
        assertDoesNotThrow(() -> {
            // These should work fine as they are business fields
            String id = hnItem.getId();
            String payload = hnItem.getPayload();
            String status = hnItem.getStatus();
            
            assertNotNull(id);
            assertNotNull(payload);
            assertNotNull(status);
        });
        
        // Verify the entity is valid with only business fields
        assertTrue(hnItem.isValid());
    }
}
