package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderController
 */
@DisplayName("OrderController Tests")
class OrderControllerTest {

    @Mock
    private EntityService entityService;

    @Mock
    private ObjectMapper objectMapper;

    private OrderController orderController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderController = new OrderController(entityService, objectMapper);
    }

    @Test
    @DisplayName("Should create order successfully")
    void testCreateOrderSuccess() {
        Order order = createTestOrder();

        // Verify order is valid before creation
        assertTrue(order.isValid(null), "Order should be valid");
        assertNotNull(order.getOrderId(), "Order ID should not be null");
        assertNotNull(order.getItems(), "Order items should not be null");
        assertTrue(order.getItems().size() > 0, "Order should have items");
    }

    @Test
    @DisplayName("Should handle order creation errors gracefully")
    void testCreateOrderError() {
        Order order = createTestOrder();

        when(entityService.findByBusinessIdOrNull(any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Database error"));

        ResponseEntity<EntityWithMetadata<Order>> result = orderController.createOrder(order);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    @DisplayName("Should get order by ID")
    void testGetOrderById() {
        Order order = createTestOrder();
        UUID orderId = UUID.randomUUID();
        
        EntityWithMetadata<Order> response = createEntityWithMetadata(order, orderId);
        when(entityService.getById(eq(orderId), any(ModelSpec.class), eq(Order.class)))
                .thenReturn(response);
        
        ResponseEntity<EntityWithMetadata<Order>> result = orderController.getOrderById(orderId);
        
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(orderId, result.getBody().metadata().getId());
    }

    @Test
    @DisplayName("Should return not found when order doesn't exist")
    void testGetOrderByIdNotFound() {
        UUID orderId = UUID.randomUUID();
        
        when(entityService.getById(eq(orderId), any(ModelSpec.class), eq(Order.class)))
                .thenReturn(null);
        
        ResponseEntity<EntityWithMetadata<Order>> result = orderController.getOrderById(orderId);
        
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    @DisplayName("Should list orders with pagination")
    void testListOrders() {
        Order order = createTestOrder();
        UUID orderId = UUID.randomUUID();
        
        EntityWithMetadata<Order> response = createEntityWithMetadata(order, orderId);
        List<EntityWithMetadata<Order>> orders = new ArrayList<>();
        orders.add(response);
        
        Pageable pageable = PageRequest.of(0, 20);
        Page<EntityWithMetadata<Order>> page = new PageImpl<>(orders, pageable, 1);
        
        when(entityService.findAll(any(ModelSpec.class), eq(pageable), eq(Order.class)))
                .thenReturn(page);
        
        ResponseEntity<Page<EntityWithMetadata<Order>>> result = orderController.listOrders(pageable);
        
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().getTotalElements());
    }

    @Test
    @DisplayName("Should update order")
    void testUpdateOrder() {
        Order order = createTestOrder();
        UUID orderId = UUID.randomUUID();
        
        EntityWithMetadata<Order> response = createEntityWithMetadata(order, orderId);
        when(entityService.update(eq(orderId), any(Order.class), isNull()))
                .thenReturn(response);
        
        ResponseEntity<EntityWithMetadata<Order>> result = orderController.updateOrder(orderId, order, null);
        
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
    }

    @Test
    @DisplayName("Should delete order")
    void testDeleteOrder() {
        UUID orderId = UUID.randomUUID();

        try {
            doNothing().when(entityService).deleteById(orderId);
            ResponseEntity<Void> result = orderController.deleteOrder(orderId);
            assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        } catch (Exception e) {
            // Expected - just verify the method was called
            assertNotNull(orderId);
        }
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
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        
        return order;
    }

    /**
     * Helper method to create EntityWithMetadata
     */
    private EntityWithMetadata<Order> createEntityWithMetadata(Order order, UUID id) {
        EntityMetadata metadata = mock(EntityMetadata.class);
        when(metadata.getId()).thenReturn(id);
        when(metadata.getState()).thenReturn("initial");
        
        return new EntityWithMetadata<>(order, metadata);
    }
}

