package com.java_template.application.entity.customer.version_1;

import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for Customer entity validation and business logic.
 */
public class CustomerTest {

    private Customer customer;
    private EntityMetadata metadata;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setCustomerId("CUST-2025-001");
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setEmail("john.doe@example.com");
        customer.setPhone("+1-555-0100");
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        metadata = new EntityMetadata();
        metadata.setId(UUID.randomUUID());
        metadata.setState("active");
    }

    @Test
    void testValidCustomerWithAllFields() {
        assertTrue(customer.isValid(metadata));
    }

    @Test
    void testValidCustomerWithoutPhone() {
        customer.setPhone(null);
        assertTrue(customer.isValid(metadata));
    }

    @Test
    void testInvalidCustomerWithoutCustomerId() {
        customer.setCustomerId(null);
        assertFalse(customer.isValid(metadata));
    }

    @Test
    void testInvalidCustomerWithoutFirstName() {
        customer.setFirstName(null);
        assertFalse(customer.isValid(metadata));
    }

    @Test
    void testInvalidCustomerWithoutLastName() {
        customer.setLastName(null);
        assertFalse(customer.isValid(metadata));
    }

    @Test
    void testInvalidCustomerWithoutEmail() {
        customer.setEmail(null);
        assertFalse(customer.isValid(metadata));
    }

    @Test
    void testInvalidEmailFormat() {
        customer.setEmail("invalid-email");
        assertFalse(customer.isValid(metadata));
    }

    @Test
    void testValidEmailFormats() {
        assertTrue(isValidEmail("user@example.com"));
        assertTrue(isValidEmail("user+tag@example.com"));
        assertTrue(isValidEmail("user.name@example.co.uk"));
    }

    @Test
    void testEntityName() {
        assertEquals("Customer", Customer.ENTITY_NAME);
    }

    @Test
    void testEntityVersion() {
        assertEquals(1, Customer.ENTITY_VERSION);
    }

    @Test
    void testGetModelKey() {
        var modelKey = customer.getModelKey();
        assertNotNull(modelKey);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}

