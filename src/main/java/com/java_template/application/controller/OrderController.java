package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: OrderController provides REST endpoints for order management including
 * CRUD operations, order state transitions, and order queries for the multi-channel retail system.
 */
@RestController
@RequestMapping("/ui/order")
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
     * POST /ui/order
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Order>> createOrder(@RequestBody Order order) {
        try {
            // Set creation timestamp
            if (order.getCreatedTimestamp() == null) {
                order.setCreatedTimestamp(LocalDateTime.now());
            }

            // Create the order
            EntityWithMetadata<Order> response = entityService.create(order);
            logger.info("Order created with ID: {}, orderId: {}", response.metadata().getId(), order.getOrderId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical ID
     * GET /ui/order/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> response = entityService.getById(id, modelSpec, Order.class);
            logger.info("Order retrieved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get order by business ID (orderId)
     * GET /ui/order/business/{orderId}
     */
    @GetMapping("/business/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderByBusinessId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> response = entityService.findByBusinessId(modelSpec, orderId, "orderId", Order.class);
            logger.info("Order retrieved with orderId: {}", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving order by orderId: {}", orderId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update order with optional workflow transition
     * PUT /ui/order/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrder(
            @PathVariable UUID id,
            @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Order> response = entityService.update(id, order, transition);
            logger.info("Order updated with ID: {}, transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order by business ID with optional workflow transition
     * PUT /ui/order/business/{orderId}?transition=TRANSITION_NAME
     */
    @PutMapping("/business/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderByBusinessId(
            @PathVariable String orderId,
            @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Order> response = entityService.updateByBusinessId(order, "orderId", transition);
            logger.info("Order updated with orderId: {}, transition: {}", orderId, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order with orderId: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete order by technical ID
     * DELETE /ui/order/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<UUID> deleteOrder(@PathVariable UUID id) {
        try {
            UUID deletedId = entityService.deleteById(id);
            logger.info("Order deleted with ID: {}", deletedId);
            return ResponseEntity.ok(deletedId);
        } catch (Exception e) {
            logger.error("Error deleting order with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Search orders by channel
     * GET /ui/order/search/channel/{channel}
     */
    @GetMapping("/search/channel/{channel}")
    public ResponseEntity<List<EntityWithMetadata<Order>>> searchOrdersByChannel(@PathVariable String channel) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition channelCondition = new SimpleCondition()
                    .withJsonPath("$.channel")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(channel));
            conditions.add(channelCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Order>> results = entityService.search(modelSpec, groupCondition, Order.class);
            logger.info("Found {} orders for channel: {}", results.size(), channel);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching orders by channel: {}", channel, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search orders by customer ID
     * GET /ui/order/search/customer/{customerId}
     */
    @GetMapping("/search/customer/{customerId}")
    public ResponseEntity<List<EntityWithMetadata<Order>>> searchOrdersByCustomer(@PathVariable String customerId) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition customerCondition = new SimpleCondition()
                    .withJsonPath("$.customer.customerId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(customerId));
            conditions.add(customerCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Order>> results = entityService.search(modelSpec, groupCondition, Order.class);
            logger.info("Found {} orders for customer: {}", results.size(), customerId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching orders by customer: {}", customerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all orders (use sparingly)
     * GET /ui/order/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<EntityWithMetadata<Order>>> getAllOrders() {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            List<EntityWithMetadata<Order>> results = entityService.findAll(modelSpec, Order.class);
            logger.info("Retrieved {} orders", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving all orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order summary (technical ID only) for performance
     * GET /ui/order/{id}/summary
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<OrderSummary> getOrderSummary(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> response = entityService.getById(id, modelSpec, Order.class);
            Order order = response.entity();
            
            OrderSummary summary = new OrderSummary();
            summary.setTechnicalId(response.metadata().getId());
            summary.setOrderId(order.getOrderId());
            summary.setChannel(order.getChannel());
            summary.setState(response.metadata().getState());
            summary.setOrderTotal(order.getOrderTotal());
            summary.setCreatedTimestamp(order.getCreatedTimestamp());
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Error retrieving order summary for ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Order summary DTO for performance-optimized responses
     */
    public static class OrderSummary {
        private UUID technicalId;
        private String orderId;
        private String channel;
        private String state;
        private Double orderTotal;
        private LocalDateTime createdTimestamp;

        // Getters and setters
        public UUID getTechnicalId() { return technicalId; }
        public void setTechnicalId(UUID technicalId) { this.technicalId = technicalId; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public Double getOrderTotal() { return orderTotal; }
        public void setOrderTotal(Double orderTotal) { this.orderTotal = orderTotal; }
        public LocalDateTime getCreatedTimestamp() { return createdTimestamp; }
        public void setCreatedTimestamp(LocalDateTime createdTimestamp) { this.createdTimestamp = createdTimestamp; }
    }
}
