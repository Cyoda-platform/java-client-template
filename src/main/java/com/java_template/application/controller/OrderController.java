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
 * OrderController - Manages order operations in the pet store
 * 
 * Base Path: /api/v1/orders
 * Description: REST controller for Order entity CRUD operations and workflow transitions
 */
@RestController
@RequestMapping("/api/v1/orders")
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
     * POST /api/v1/orders
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
            logger.error("Error creating Order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical UUID
     * GET /api/v1/orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.getById(id, modelSpec, Order.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get order by business identifier
     * GET /api/v1/orders/business/{orderId}
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
            logger.error("Error getting Order by business ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order with optional workflow transition
     * PUT /api/v1/orders/{id}?transitionName=TRANSITION_NAME
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
            logger.error("Error updating Order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete order by technical UUID
     * DELETE /api/v1/orders/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Order deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get orders with optional filtering
     * GET /api/v1/orders?customerId=user-001&status=placed
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Order>>> getAllOrders(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            
            if (customerId != null || status != null) {
                List<SimpleCondition> conditions = new ArrayList<>();
                
                if (customerId != null && !customerId.trim().isEmpty()) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.customerInfo.customerId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(customerId)));
                }

                if (!conditions.isEmpty()) {
                    GroupCondition condition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(new ArrayList<QueryCondition>(conditions));

                    List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, condition, Order.class);
                    
                    // Filter by status (entity state) if provided
                    if (status != null) {
                        orders = orders.stream()
                                .filter(order -> status.equals(order.metadata().getState()))
                                .toList();
                    }
                    
                    return ResponseEntity.ok(orders);
                }
            }
            
            List<EntityWithMetadata<Order>> orders = entityService.findAll(modelSpec, Order.class);
            
            // Filter by status if provided
            if (status != null) {
                orders = orders.stream()
                        .filter(order -> status.equals(order.metadata().getState()))
                        .toList();
            }
            
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting all Orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search orders by advanced criteria
     * POST /api/v1/orders/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Order>>> searchOrders(@RequestBody OrderSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getCustomerId() != null && !searchRequest.getCustomerId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.customerInfo.customerId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCustomerId())));
            }

            if (searchRequest.getPetId() != null && !searchRequest.getPetId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.petId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPetId())));
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

            if (searchRequest.getPaymentMethod() != null && !searchRequest.getPaymentMethod().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.paymentMethod")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPaymentMethod())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<QueryCondition>(conditions));

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, condition, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error performing order search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for order search requests
     */
    @Getter
    @Setter
    public static class OrderSearchRequest {
        private String customerId;
        private String petId;
        private Double minAmount;
        private Double maxAmount;
        private String paymentMethod;
    }
}
