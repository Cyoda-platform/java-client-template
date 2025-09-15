package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
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
 * DeliveryService Controller - Manages delivery service entities and workflow transitions
 * Provides CRUD operations and workflow state management for delivery services
 */
@RestController
@RequestMapping("/api/delivery-services")
@CrossOrigin(origins = "*")
public class DeliveryServiceController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryServiceController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new delivery service
     * POST /api/delivery-services
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<DeliveryService>> createDeliveryService(@RequestBody DeliveryService deliveryService) {
        try {
            deliveryService.setCreatedAt(LocalDateTime.now());
            deliveryService.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DeliveryService> response = entityService.create(deliveryService);
            logger.info("DeliveryService created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating delivery service", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get delivery service by technical UUID
     * GET /api/delivery-services/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DeliveryService>> getDeliveryServiceById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryService.ENTITY_NAME).withVersion(DeliveryService.ENTITY_VERSION);
            EntityWithMetadata<DeliveryService> response = entityService.getById(id, modelSpec, DeliveryService.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting delivery service by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get delivery service by business identifier
     * GET /api/delivery-services/business/{deliveryServiceId}
     */
    @GetMapping("/business/{deliveryServiceId}")
    public ResponseEntity<EntityWithMetadata<DeliveryService>> getDeliveryServiceByBusinessId(@PathVariable String deliveryServiceId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryService.ENTITY_NAME).withVersion(DeliveryService.ENTITY_VERSION);
            EntityWithMetadata<DeliveryService> response = entityService.findByBusinessId(
                    modelSpec, deliveryServiceId, "deliveryServiceId", DeliveryService.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting delivery service by business ID: {}", deliveryServiceId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update delivery service with optional workflow transition
     * PUT /api/delivery-services/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DeliveryService>> updateDeliveryService(
            @PathVariable UUID id,
            @RequestBody DeliveryService deliveryService,
            @RequestParam(required = false) String transition) {
        try {
            deliveryService.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DeliveryService> response = entityService.update(id, deliveryService, transition);
            logger.info("DeliveryService updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating delivery service", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete delivery service by technical UUID
     * DELETE /api/delivery-services/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeliveryService(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("DeliveryService deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting delivery service", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all delivery services
     * GET /api/delivery-services
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<DeliveryService>>> getAllDeliveryServices() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryService.ENTITY_NAME).withVersion(DeliveryService.ENTITY_VERSION);
            List<EntityWithMetadata<DeliveryService>> deliveryServices = entityService.findAll(modelSpec, DeliveryService.class);
            return ResponseEntity.ok(deliveryServices);
        } catch (Exception e) {
            logger.error("Error getting all delivery services", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search delivery services by region
     * GET /api/delivery-services/search?region=regionName
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<DeliveryService>>> searchDeliveryServicesByRegion(
            @RequestParam String region) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryService.ENTITY_NAME).withVersion(DeliveryService.ENTITY_VERSION);

            SimpleCondition regionCondition = new SimpleCondition()
                    .withJsonPath("$.supportedRegions")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(region));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(regionCondition));

            List<EntityWithMetadata<DeliveryService>> deliveryServices = entityService.search(modelSpec, condition, DeliveryService.class);
            return ResponseEntity.ok(deliveryServices);
        } catch (Exception e) {
            logger.error("Error searching delivery services by region: {}", region, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for delivery services
     * POST /api/delivery-services/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<DeliveryService>>> advancedSearch(
            @RequestBody DeliveryServiceSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryService.ENTITY_NAME).withVersion(DeliveryService.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getIsActive() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isActive")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsActive())));
            }

            if (searchRequest.getMinCommissionRate() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.commissionRate")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinCommissionRate())));
            }

            if (searchRequest.getMaxCommissionRate() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.commissionRate")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxCommissionRate())));
            }

            if (searchRequest.getRegion() != null && !searchRequest.getRegion().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.supportedRegions")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getRegion())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<DeliveryService>> deliveryServices = entityService.search(modelSpec, condition, DeliveryService.class);
            return ResponseEntity.ok(deliveryServices);
        } catch (Exception e) {
            logger.error("Error performing advanced delivery service search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced delivery service search requests
     */
    @Getter
    @Setter
    public static class DeliveryServiceSearchRequest {
        private String name;
        private Boolean isActive;
        private Double minCommissionRate;
        private Double maxCommissionRate;
        private String region;
    }
}
