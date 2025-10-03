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
 * REST Controller for Order management
 * Provides CRUD operations, status updates, and order queries
 */
@RestController
@RequestMapping("/ui/orders")
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
     * POST /ui/orders
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Order>> createOrder(@RequestBody Order order) {
        try {
            // Set creation timestamp
            order.setCreatedTimestamp(LocalDateTime.now());
            order.setUpdatedTimestamp(LocalDateTime.now());
            order.setLastUpdatedBy("OrderController");

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
     * GET /ui/orders/{id}
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
     * Get order by business ID (orderId)
     * GET /ui/orders/business/{orderId}
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
     * PUT /ui/orders/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrder(
            @PathVariable UUID id,
            @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        try {
            order.setUpdatedTimestamp(LocalDateTime.now());
            order.setLastUpdatedBy("OrderController");

            EntityWithMetadata<Order> response = entityService.update(id, order, transition);
            logger.info("Order updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Submit order for processing
     * POST /ui/orders/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<EntityWithMetadata<Order>> submitOrder(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.getById(id, modelSpec, Order.class);
            
            Order order = orderResponse.entity();
            order.setUpdatedTimestamp(LocalDateTime.now());
            order.setLastUpdatedBy("OrderController");

            EntityWithMetadata<Order> response = entityService.update(id, order, "submit_order");
            logger.info("Order submitted with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel order
     * POST /ui/orders/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<EntityWithMetadata<Order>> cancelOrder(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderResponse = entityService.getById(id, modelSpec, Order.class);
            
            Order order = orderResponse.entity();
            order.setUpdatedTimestamp(LocalDateTime.now());
            order.setLastUpdatedBy("OrderController");

            EntityWithMetadata<Order> response = entityService.update(id, order, "cancel_order");
            logger.info("Order cancelled with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all orders
     * GET /ui/orders
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
     * Search orders by channel
     * GET /ui/orders/search?channel=web
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Order>>> searchOrdersByChannel(
            @RequestParam String channel) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.channel")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(channel));
            conditions.add(condition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, groupCondition, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error searching orders by channel: {}", channel, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for orders
     * POST /ui/orders/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Order>>> advancedSearch(
            @RequestBody OrderSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getChannel() != null && !searchRequest.getChannel().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.channel")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getChannel())));
            }

            if (searchRequest.getCustomerId() != null && !searchRequest.getCustomerId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.customer.customerId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCustomerId())));
            }

            if (searchRequest.getMinAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.totalAmount")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinAmount())));
            }

            if (searchRequest.getMaxAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.totalAmount")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxAmount())));
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, groupCondition, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error performing advanced search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete order by technical UUID
     * DELETE /ui/orders/{id}
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
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class OrderSearchRequest {
        private String channel;
        private String customerId;
        private Double minAmount;
        private Double maxAmount;
    }
}
