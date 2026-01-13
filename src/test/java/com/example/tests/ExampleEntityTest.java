package com.example.tests;

import com.example.application.entity.example_entity.version_1.ExampleEntity;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExampleEntity
 * Tests entity validation, nested classes, and model key generation
 */
@DisplayName("ExampleEntity Tests")
class ExampleEntityTest {

    private ExampleEntity entity;
    private EntityMetadata metadata;

    @BeforeEach
    void setUp() {
        entity = new ExampleEntity();
        metadata = new EntityMetadata();
    }

    @Test
    @DisplayName("Should have correct entity name constant")
    void testEntityNameConstant() {
        assertEquals("ExampleEntity", ExampleEntity.ENTITY_NAME);
    }

    @Test
    @DisplayName("Should have correct entity version constant")
    void testEntityVersionConstant() {
        assertEquals(1, ExampleEntity.ENTITY_VERSION);
    }

    @Test
    @DisplayName("Should return correct model key")
    void testGetModelKey() {
        var modelKey = entity.getModelKey();
        assertNotNull(modelKey);
        assertTrue(modelKey instanceof com.java_template.common.workflow.OperationSpecification.Entity);
        
        var entitySpec = (com.java_template.common.workflow.OperationSpecification.Entity) modelKey;
        ModelSpec modelSpec = entitySpec.modelKey();
        assertEquals("ExampleEntity", modelSpec.getName());
        assertEquals(1, modelSpec.getVersion());
    }

    @Test
    @DisplayName("Should be valid when exampleId is set")
    void testIsValidWithExampleId() {
        entity.setExampleId("TEST-001");
        assertTrue(entity.isValid(metadata));
    }

    @Test
    @DisplayName("Should be invalid when exampleId is null")
    void testIsInvalidWhenExampleIdIsNull() {
        entity.setExampleId(null);
        assertFalse(entity.isValid(metadata));
    }

    @Test
    @DisplayName("Should set and get all basic fields")
    void testBasicFieldsSettersAndGetters() {
        entity.setExampleId("TEST-001");
        entity.setName("Test Entity");
        entity.setAmount(100.50);
        entity.setQuantity(5);
        entity.setDescription("Test description");

        assertEquals("TEST-001", entity.getExampleId());
        assertEquals("Test Entity", entity.getName());
        assertEquals(100.50, entity.getAmount());
        assertEquals(5, entity.getQuantity());
        assertEquals("Test description", entity.getDescription());
    }

    @Test
    @DisplayName("Should set and get timestamp fields")
    void testTimestampFields() {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should handle items list")
    void testItemsList() {
        List<ExampleEntity.ExampleItem> items = new ArrayList<>();
        
        ExampleEntity.ExampleItem item1 = new ExampleEntity.ExampleItem();
        item1.setItemId("ITEM-001");
        item1.setItemName("Item 1");
        item1.setPrice(10.0);
        item1.setQty(2);
        item1.setCategory("Category A");
        item1.setItemTotal(20.0);
        items.add(item1);

        ExampleEntity.ExampleItem item2 = new ExampleEntity.ExampleItem();
        item2.setItemId("ITEM-002");
        item2.setItemName("Item 2");
        item2.setPrice(15.0);
        item2.setQty(3);
        item2.setCategory("Category B");
        item2.setItemTotal(45.0);
        items.add(item2);

        entity.setItems(items);

        assertNotNull(entity.getItems());
        assertEquals(2, entity.getItems().size());
        assertEquals("ITEM-001", entity.getItems().get(0).getItemId());
        assertEquals("ITEM-002", entity.getItems().get(1).getItemId());
    }

    @Test
    @DisplayName("Should handle contact with nested address")
    void testContactWithAddress() {
        ExampleEntity.ExampleAddress address = new ExampleEntity.ExampleAddress();
        address.setLine1("123 Main St");
        address.setLine2("Apt 4B");
        address.setCity("Springfield");
        address.setState("IL");
        address.setPostcode("62701");
        address.setCountry("USA");

        ExampleEntity.ExampleContact contact = new ExampleEntity.ExampleContact();
        contact.setName("John Doe");
        contact.setEmail("john.doe@example.com");
        contact.setPhone("+1-555-1234");
        contact.setAddress(address);

        entity.setContact(contact);

        assertNotNull(entity.getContact());
        assertEquals("John Doe", entity.getContact().getName());
        assertEquals("john.doe@example.com", entity.getContact().getEmail());
        assertEquals("+1-555-1234", entity.getContact().getPhone());
        
        assertNotNull(entity.getContact().getAddress());
        assertEquals("123 Main St", entity.getContact().getAddress().getLine1());
        assertEquals("Springfield", entity.getContact().getAddress().getCity());
        assertEquals("USA", entity.getContact().getAddress().getCountry());
    }

    @Test
    @DisplayName("ExampleItem should set and get all fields")
    void testExampleItemFields() {
        ExampleEntity.ExampleItem item = new ExampleEntity.ExampleItem();
        item.setItemId("ITEM-001");
        item.setItemName("Test Item");
        item.setPrice(25.99);
        item.setQty(3);
        item.setCategory("Electronics");
        item.setItemTotal(77.97);

        assertEquals("ITEM-001", item.getItemId());
        assertEquals("Test Item", item.getItemName());
        assertEquals(25.99, item.getPrice());
        assertEquals(3, item.getQty());
        assertEquals("Electronics", item.getCategory());
        assertEquals(77.97, item.getItemTotal());
    }

    @Test
    @DisplayName("ExampleContact should set and get all fields")
    void testExampleContactFields() {
        ExampleEntity.ExampleContact contact = new ExampleEntity.ExampleContact();
        contact.setName("Jane Smith");
        contact.setEmail("jane@example.com");
        contact.setPhone("+1-555-5678");

        assertEquals("Jane Smith", contact.getName());
        assertEquals("jane@example.com", contact.getEmail());
        assertEquals("+1-555-5678", contact.getPhone());
    }

    @Test
    @DisplayName("ExampleAddress should set and get all fields")
    void testExampleAddressFields() {
        ExampleEntity.ExampleAddress address = new ExampleEntity.ExampleAddress();
        address.setLine1("456 Oak Ave");
        address.setLine2("Suite 200");
        address.setCity("Chicago");
        address.setState("IL");
        address.setPostcode("60601");
        address.setCountry("USA");

        assertEquals("456 Oak Ave", address.getLine1());
        assertEquals("Suite 200", address.getLine2());
        assertEquals("Chicago", address.getCity());
        assertEquals("IL", address.getState());
        assertEquals("60601", address.getPostcode());
        assertEquals("USA", address.getCountry());
    }

    @Test
    @DisplayName("Should handle null optional fields")
    void testNullOptionalFields() {
        entity.setExampleId("TEST-001");
        entity.setDescription(null);
        entity.setItems(null);
        entity.setContact(null);
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);

        assertTrue(entity.isValid(metadata));
        assertNull(entity.getDescription());
        assertNull(entity.getItems());
        assertNull(entity.getContact());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should support Lombok equals and hashCode")
    void testEqualsAndHashCode() {
        ExampleEntity entity1 = new ExampleEntity();
        entity1.setExampleId("TEST-001");
        entity1.setName("Test");
        entity1.setAmount(100.0);

        ExampleEntity entity2 = new ExampleEntity();
        entity2.setExampleId("TEST-001");
        entity2.setName("Test");
        entity2.setAmount(100.0);

        ExampleEntity entity3 = new ExampleEntity();
        entity3.setExampleId("TEST-002");
        entity3.setName("Different");
        entity3.setAmount(200.0);

        assertEquals(entity1, entity2);
        assertNotEquals(entity1, entity3);
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }

    @Test
    @DisplayName("Should support Lombok toString")
    void testToString() {
        entity.setExampleId("TEST-001");
        entity.setName("Test Entity");
        
        String toString = entity.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("ExampleEntity"));
        assertTrue(toString.contains("TEST-001"));
        assertTrue(toString.contains("Test Entity"));
    }
}

