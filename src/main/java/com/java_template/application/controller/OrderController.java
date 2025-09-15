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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order Controller - Manages order entities and workflow transitions
 * Provides CRUD operations and workflow state management for orders
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
     * Create a new order
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Order>> createOrder(@RequestBody Order order) {
        try {
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
     * Update order with optional workflow transition
     * PUT /api/orders/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrder(
            @PathVariable UUID id,
            @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        try {
            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> response = entityService.update(id, order, transition);
            logger.info("Order updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete order by technical UUID
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

    /**
     * Get all orders
     * GET /api/orders
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Order>>> getAllOrders() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            List<EntityWithMetadata<Order>> orders = entityService.findAll(modelSpec, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting all orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get orders by restaurant
     * GET /api/orders/restaurant/{restaurantId}
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<EntityWithMetadata<Order>>> getOrdersByRestaurant(@PathVariable String restaurantId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, condition, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting orders by restaurant: {}", restaurantId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get orders by customer
     * GET /api/orders/customer/{customerId}
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<EntityWithMetadata<Order>>> getOrdersByCustomer(@PathVariable String customerId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);

            SimpleCondition customerCondition = new SimpleCondition()
                    .withJsonPath("$.customerId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(customerId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(customerCondition));

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, condition, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting orders by customer: {}", customerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for orders
     * POST /api/orders/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Order>>> advancedSearch(
            @RequestBody OrderSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getRestaurantId() != null && !searchRequest.getRestaurantId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.restaurantId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getRestaurantId())));
            }

            if (searchRequest.getCustomerId() != null && !searchRequest.getCustomerId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.customerId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCustomerId())));
            }

            if (searchRequest.getPaymentStatus() != null && !searchRequest.getPaymentStatus().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.paymentStatus")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPaymentStatus())));
            }

            if (searchRequest.getMinTotalAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.totalAmount")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinTotalAmount())));
            }

            if (searchRequest.getMaxTotalAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.totalAmount")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxTotalAmount())));
            }

            if (searchRequest.getDeliveryPersonId() != null && !searchRequest.getDeliveryPersonId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.deliveryPersonId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getDeliveryPersonId())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, condition, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error performing advanced order search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced order search requests
     */
    @Getter
    @Setter
    public static class OrderSearchRequest {
        private String restaurantId;
        private String customerId;
        private String paymentStatus;
        private Double minTotalAmount;
        private Double maxTotalAmount;
        private String deliveryPersonId;
    }
}
