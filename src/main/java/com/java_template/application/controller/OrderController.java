package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OrderController - Manage customer orders
 * 
 * Base Path: /api/orders
 * Entity: Order
 * Purpose: Manage customer orders
 */
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get all orders with filtering
     * GET /api/orders
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Order>>> getAllOrders(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            List<EntityWithMetadata<Order>> allOrders;

            if (userId != null) {
                // Search by userId
                SimpleCondition userCondition = new SimpleCondition()
                        .withJsonPath("$.userId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(userId));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(userCondition));

                allOrders = entityService.search(modelSpec, condition, Order.class);
            } else {
                allOrders = entityService.findAll(modelSpec, Order.class);
            }

            // Filter by status if provided (status is in metadata)
            if (status != null) {
                allOrders = allOrders.stream()
                        .filter(order -> status.equals(order.metadata().getState()))
                        .collect(Collectors.toList());
            }

            // Apply pagination
            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allOrders.size());
            
            List<EntityWithMetadata<Order>> pageContent = allOrders.subList(start, end);
            Page<EntityWithMetadata<Order>> orderPage = new PageImpl<>(pageContent, pageable, allOrders.size());

            return ResponseEntity.ok(orderPage);
        } catch (Exception e) {
            logger.error("Error getting orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical UUID
     * GET /api/orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.getById(id, modelSpec, Order.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get order by business identifier
     * GET /api/orders/business/{orderId}
     */
    @GetMapping("/business/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderByBusinessId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order by business ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create new order
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Order>> createOrder(@RequestBody Order order) {
        try {
            // Set creation timestamp
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> response = entityService.create(order);
            logger.info("Order created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order with optional workflow transition
     * PUT /api/orders/{id}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrder(
            @PathVariable UUID id,
            @RequestBody Order order,
            @RequestParam(required = false) String transitionName) {
        try {
            // Set update timestamp
            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> response = entityService.update(id, order, transitionName);
            logger.info("Order updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Approve order
     * PUT /api/orders/{orderId}/approve
     */
    @PutMapping("/{orderId}/approve")
    public ResponseEntity<EntityWithMetadata<Order>> approveOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderEntity = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Order> response = entityService.update(
                    orderEntity.metadata().getId(), orderEntity.entity(), "approve_order");
            logger.info("Order {} approved", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error approving order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Ship order
     * PUT /api/orders/{orderId}/ship
     */
    @PutMapping("/{orderId}/ship")
    public ResponseEntity<EntityWithMetadata<Order>> shipOrder(
            @PathVariable String orderId,
            @RequestBody(required = false) ShippingRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderEntity = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Order> response = entityService.update(
                    orderEntity.metadata().getId(), orderEntity.entity(), "ship_order");
            logger.info("Order {} shipped", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error shipping order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel order
     * PUT /api/orders/{orderId}/cancel
     */
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<EntityWithMetadata<Order>> cancelOrder(
            @PathVariable String orderId,
            @RequestBody(required = false) CancellationRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderEntity = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderEntity == null) {
                return ResponseEntity.notFound().build();
            }

            // Determine transition based on current state
            String currentState = orderEntity.metadata().getState();
            String transition = "placed".equals(currentState) ? "cancel_order" : "cancel_approved_order";

            EntityWithMetadata<Order> response = entityService.update(
                    orderEntity.metadata().getId(), orderEntity.entity(), transition);
            logger.info("Order {} cancelled", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Confirm delivery
     * PUT /api/orders/{orderId}/confirm-delivery
     */
    @PutMapping("/{orderId}/confirm-delivery")
    public ResponseEntity<EntityWithMetadata<Order>> confirmDelivery(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderEntity = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Order> response = entityService.update(
                    orderEntity.metadata().getId(), orderEntity.entity(), "confirm_delivery");
            logger.info("Order {} delivery confirmed", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error confirming delivery for order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete order
     * DELETE /api/orders/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Order deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for shipping requests
     */
    @Getter
    @Setter
    public static class ShippingRequest {
        private String trackingNumber;
        private String carrier;
        private LocalDateTime estimatedDelivery;
    }

    /**
     * DTO for cancellation requests
     */
    @Getter
    @Setter
    public static class CancellationRequest {
        private String reason;
        private Double refundAmount;
    }
}
