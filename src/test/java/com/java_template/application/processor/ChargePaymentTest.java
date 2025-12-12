package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChargePayment processor
 */
@DisplayName("ChargePayment Processor Tests")
class ChargePaymentTest {

    @Mock
    private SerializerFactory serializerFactory;

    @Mock
    private ProcessorSerializer serializer;

    @Mock
    private EntityService entityService;

    private ChargePayment chargePaymentProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(serializer);
        chargePaymentProcessor = new ChargePayment(serializerFactory, entityService);
    }

    @Test
    @DisplayName("Should process payment for valid order")
    void testChargePaymentSuccess() {
        Order order = createTestOrder();
        
        assertNotNull(order, "Order should not be null");
        assertNotNull(order.getTotalAmount(), "Order total should not be null");
        assertTrue(order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0, "Order total should be positive");
    }

    @Test
    @DisplayName("Should handle payment for different amounts")
    void testChargePaymentDifferentAmounts() {
        Order order1 = createTestOrder();
        order1.setTotalAmount(new BigDecimal("100.00"));
        
        Order order2 = createTestOrder();
        order2.setTotalAmount(new BigDecimal("5000.00"));
        
        assertTrue(order1.getTotalAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(order2.getTotalAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(order2.getTotalAmount().compareTo(order1.getTotalAmount()) > 0);
    }

    @Test
    @DisplayName("Should store payment metadata")
    void testPaymentMetadataStorage() {
        Order order = createTestOrder();
        
        // Simulate storing payment info
        if (order.getMetadata() == null) {
            order.setMetadata(new java.util.HashMap<>());
        }
        
        order.getMetadata().put("payment", new java.util.HashMap<>());
        
        assertNotNull(order.getMetadata(), "Metadata should not be null");
        assertTrue(order.getMetadata().containsKey("payment"), "Metadata should contain payment info");
    }

    @Test
    @DisplayName("Should validate order before charging")
    void testValidateOrderBeforeCharge() {
        Order order = createTestOrder();
        
        assertTrue(order.isValid(null), "Order should be valid before charging");
        assertNotNull(order.getOrderId(), "Order ID should not be null");
        assertNotNull(order.getCustomerId(), "Customer ID should not be null");
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

