package com.java_template.application.controller;

import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PetCareOrderController - REST API controller for PetCareOrder entity management
 * 
 * Provides CRUD operations and workflow state transitions for pet care orders
 * in the Purrfect Pets API system.
 */
@RestController
@RequestMapping("/ui/order")
@CrossOrigin(origins = "*")
public class PetCareOrderController {

    private static final Logger logger = LoggerFactory.getLogger(PetCareOrderController.class);
    private final EntityService entityService;

    public PetCareOrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new pet care service order
     * POST /ui/order
     * Triggers create_order transition (none → PENDING)
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<PetCareOrder>> createOrder(@RequestBody PetCareOrder order) {
        try {
            // Set order timestamp
            order.setOrderDate(LocalDateTime.now());
            
            // Create order entity - this will trigger the create_order transition
            EntityWithMetadata<PetCareOrder> response = entityService.create(order);
            logger.info("Order created with ID: {} and orderId: {}", response.getId(), order.getOrderId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical UUID (FASTEST method)
     * GET /ui/order/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<PetCareOrder>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            EntityWithMetadata<PetCareOrder> response = entityService.getById(id, modelSpec, PetCareOrder.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get order by business identifier (orderId)
     * GET /ui/order/business/{orderId}
     */
    @GetMapping("/business/{orderId}")
    public ResponseEntity<EntityWithMetadata<PetCareOrder>> getOrderByBusinessId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            EntityWithMetadata<PetCareOrder> response = entityService.findByBusinessId(
                modelSpec, orderId, "orderId", PetCareOrder.class);

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
     * Get all orders for a specific pet
     * GET /ui/order/pet/{petId}
     */
    @GetMapping("/pet/{petId}")
    public ResponseEntity<List<EntityWithMetadata<PetCareOrder>>> getOrdersByPet(@PathVariable String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Create search condition for petId
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.petId", "EQUALS", petId)
            );

            List<EntityWithMetadata<PetCareOrder>> orders = entityService.search(modelSpec, searchRequest, PetCareOrder.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting Orders by pet: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all orders placed by a specific owner
     * GET /ui/order/owner/{ownerId}
     */
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<EntityWithMetadata<PetCareOrder>>> getOrdersByOwner(@PathVariable String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Create search condition for ownerId
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.ownerId", "EQUALS", ownerId)
            );

            List<EntityWithMetadata<PetCareOrder>> orders = entityService.search(modelSpec, searchRequest, PetCareOrder.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting Orders by owner: {}", ownerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order with optional workflow transition
     * PUT /ui/order/{id}?transition={transitionName}
     * 
     * Supported transitions:
     * - confirm_order (PENDING → CONFIRMED)
     * - start_service (CONFIRMED → IN_PROGRESS)
     * - complete_service (IN_PROGRESS → COMPLETED)
     * - cancel_order (PENDING/CONFIRMED → CANCELLED)
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<PetCareOrder>> updateOrder(
            @PathVariable UUID id,
            @RequestBody PetCareOrder order,
            @RequestParam(required = false) String transition) {
        try {
            // Set completion date if completing service
            if ("complete_service".equals(transition) && order.getCompletionDate() == null) {
                order.setCompletionDate(LocalDateTime.now());
            }
            
            // Update order entity with optional transition
            EntityWithMetadata<PetCareOrder> response = entityService.update(id, order, transition);
            logger.info("Order updated with ID: {} and transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Order with ID: {} and transition: {}", id, transition, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete order by technical UUID
     * DELETE /ui/order/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Order deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all orders (USE SPARINGLY - can be slow for large datasets)
     * GET /ui/order
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<PetCareOrder>>> getAllOrders() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            List<EntityWithMetadata<PetCareOrder>> orders = entityService.findAll(modelSpec, PetCareOrder.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting all Orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search orders by service type
     * GET /ui/order/search?serviceType=GROOMING
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<PetCareOrder>>> searchOrdersByServiceType(
            @RequestParam String serviceType) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Create search condition
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.serviceType", "EQUALS", serviceType)
            );

            List<EntityWithMetadata<PetCareOrder>> orders = entityService.search(modelSpec, searchRequest, PetCareOrder.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error searching Orders by service type: {}", serviceType, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for orders
     * POST /ui/order/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<PetCareOrder>>> advancedSearch(
            @RequestBody OrderSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Build complex search condition
            List<Condition> conditions = new ArrayList<>();

            if (searchRequest.getServiceType() != null && !searchRequest.getServiceType().trim().isEmpty()) {
                conditions.add(Condition.of("$.serviceType", "EQUALS", searchRequest.getServiceType()));
            }

            if (searchRequest.getPetId() != null && !searchRequest.getPetId().trim().isEmpty()) {
                conditions.add(Condition.of("$.petId", "EQUALS", searchRequest.getPetId()));
            }

            if (searchRequest.getOwnerId() != null && !searchRequest.getOwnerId().trim().isEmpty()) {
                conditions.add(Condition.of("$.ownerId", "EQUALS", searchRequest.getOwnerId()));
            }

            if (searchRequest.getMinCost() != null) {
                conditions.add(Condition.of("$.cost", "GREATER_THAN_OR_EQUAL", searchRequest.getMinCost()));
            }

            if (searchRequest.getMaxCost() != null) {
                conditions.add(Condition.of("$.cost", "LESS_THAN_OR_EQUAL", searchRequest.getMaxCost()));
            }

            if (searchRequest.getPaymentMethod() != null && !searchRequest.getPaymentMethod().trim().isEmpty()) {
                conditions.add(Condition.of("$.paymentMethod", "EQUALS", searchRequest.getPaymentMethod()));
            }

            SearchConditionRequest searchConditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            List<EntityWithMetadata<PetCareOrder>> orders = entityService.search(modelSpec, searchConditionRequest, PetCareOrder.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error performing advanced order search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    public static class OrderSearchRequest {
        private String serviceType;
        private String petId;
        private String ownerId;
        private Double minCost;
        private Double maxCost;
        private String paymentMethod;

        // Getters and setters
        public String getServiceType() { return serviceType; }
        public void setServiceType(String serviceType) { this.serviceType = serviceType; }
        
        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
        
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        
        public Double getMinCost() { return minCost; }
        public void setMinCost(Double minCost) { this.minCost = minCost; }
        
        public Double getMaxCost() { return maxCost; }
        public void setMaxCost(Double maxCost) { this.maxCost = maxCost; }
        
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    }
}
