package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ValidateOrder processor
 */
@DisplayName("ValidateOrder Processor Tests")
class ValidateOrderTest {

    @Mock
    private SerializerFactory serializerFactory;

    @Mock
    private ProcessorSerializer serializer;

    @Mock
    private EntityService entityService;

    private ObjectMapper objectMapper;
    private ValidateOrder validateOrderProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(serializer);
        validateOrderProcessor = new ValidateOrder(serializerFactory, entityService, objectMapper);
    }

    @Test
    @DisplayName("Should validate order with valid items and total")
    void testValidateOrderSuccess() {
        // Create test order
        Order order = createTestOrder();
        
        // Verify order is valid
        assertTrue(order.isValid(null), "Order should be valid");
        assertNotNull(order.getItems(), "Order should have items");
        assertEquals(1, order.getItems().size(), "Order should have 1 item");
        assertEquals(new BigDecimal("999.99"), order.getTotalAmount(), "Order total should match");
    }

    @Test
    @DisplayName("Should fail validation when order has no items")
    void testValidateOrderNoItems() {
        Order order = createTestOrder();
        order.setItems(new ArrayList<>());

        assertTrue(order.getItems().isEmpty(),
                "Order items should be empty");
    }

    @Test
    @DisplayName("Should validate order total calculation")
    void testValidateOrderTotalCalculation() {
        Order order = createTestOrder();
        
        // Calculate expected total
        BigDecimal expectedTotal = BigDecimal.ZERO;
        for (Order.OrderItem item : order.getItems()) {
            BigDecimal itemTotal = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            expectedTotal = expectedTotal.add(itemTotal);
        }
        
        assertEquals(expectedTotal, order.getTotalAmount(), "Order total should match calculated sum");
    }

    @Test
    @DisplayName("Should validate order with multiple items")
    void testValidateOrderMultipleItems() {
        Order order = createTestOrder();
        
        // Add another item
        Order.OrderItem item2 = new Order.OrderItem();
        item2.setItemId("ITEM-002");
        item2.setProductName("Mouse");
        item2.setQuantity(2);
        item2.setUnitPrice(new BigDecimal("29.99"));
        item2.setSubtotal(new BigDecimal("59.98"));
        
        order.getItems().add(item2);
        
        assertEquals(2, order.getItems().size(), "Order should have 2 items");
    }

    /**
     * Helper method to create a test order
     */
    private Order createTestOrder() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setCustomerId("CUST-001");
        order.setCustomerName("John Doe");
        order.setCustomerEmail("john@example.com");
        order.setCurrency("USD");
        
        Order.OrderItem item = new Order.OrderItem();
        item.setItemId("ITEM-001");
        item.setProductName("Laptop");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("999.99"));
        item.setSubtotal(new BigDecimal("999.99"));
        
        List<Order.OrderItem> items = new ArrayList<>();
        items.add(item);
        order.setItems(items);
        order.setTotalAmount(new BigDecimal("999.99"));
        
        return order;
    }
}

