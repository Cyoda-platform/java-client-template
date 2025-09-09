package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order_entity.version_1.OrderEntity;
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
 * OrderController - REST controller for Order entity operations
 * Base Path: /api/orders
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
     * Create a new order entity
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<OrderEntity>> createOrder(@RequestBody OrderEntity entity) {
        try {
            // Set order date if not provided
            if (entity.getOrderDate() == null) {
                entity.setOrderDate(LocalDateTime.now());
            }

            // Calculate total amount if not provided
            if (entity.getTotalAmount() == null && entity.getQuantity() != null && entity.getUnitPrice() != null) {
                entity.setTotalAmount(entity.getQuantity() * entity.getUnitPrice());
            }

            // Set default completion status
            if (entity.getComplete() == null) {
                entity.setComplete(false);
            }

            EntityWithMetadata<OrderEntity> response = entityService.create(entity);
            logger.info("Order created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical UUID
     * GET /api/orders/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<OrderEntity>> getOrderById(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(OrderEntity.ENTITY_NAME).withVersion(OrderEntity.ENTITY_VERSION);
            EntityWithMetadata<OrderEntity> response = entityService.getById(uuid, modelSpec, OrderEntity.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Order by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update order entity with optional state transition
     * PUT /api/orders/{uuid}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<OrderEntity>> updateOrder(
            @PathVariable UUID uuid,
            @RequestBody OrderEntity entity,
            @RequestParam(required = false) String transitionName) {
        try {
            // Recalculate total amount if quantity or unit price changed
            if (entity.getQuantity() != null && entity.getUnitPrice() != null) {
                entity.setTotalAmount(entity.getQuantity() * entity.getUnitPrice());
            }

            EntityWithMetadata<OrderEntity> response = entityService.update(uuid, entity, transitionName);
            logger.info("Order updated with ID: {}", uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete order entity
     * DELETE /api/orders/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID uuid) {
        try {
            entityService.deleteById(uuid);
            logger.info("Order deleted with ID: {}", uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all orders with optional filtering
     * GET /api/orders?petId=1&status=completed&startDate=2024-01-01&endDate=2024-01-31
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<OrderEntity>>> getAllOrders(
            @RequestParam(required = false) Long petId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(OrderEntity.ENTITY_NAME).withVersion(OrderEntity.ENTITY_VERSION);
            
            // Build search conditions
            List<QueryCondition> conditions = new ArrayList<>();
            
            if (petId != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.petId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(petId)));
            }

            if (status != null && !status.trim().isEmpty()) {
                // Note: status filtering would be based on entity state in metadata
                // For simplicity, we'll filter by complete field
                boolean isCompleted = "completed".equalsIgnoreCase(status);
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.complete")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(isCompleted)));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<OrderEntity>> entities = entityService.search(modelSpec, condition, OrderEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all Orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search orders by criteria
     * POST /api/orders/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<OrderEntity>>> searchOrders(@RequestBody OrderSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(OrderEntity.ENTITY_NAME).withVersion(OrderEntity.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getPetId() != null) {
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

            if (searchRequest.getComplete() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.complete")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getComplete())));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<OrderEntity>> entities = entityService.search(modelSpec, condition, OrderEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching Orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for order search requests
     */
    @Getter
    @Setter
    public static class OrderSearchRequest {
        private Long petId;
        private Double minAmount;
        private Double maxAmount;
        private Boolean complete;
        private String customerEmail;
    }
}
