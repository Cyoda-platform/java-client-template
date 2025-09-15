package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryperson.version_1.DeliveryPerson;
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
 * DeliveryPerson Controller - Manages delivery person entities and workflow transitions
 * Provides CRUD operations and workflow state management for delivery persons
 */
@RestController
@RequestMapping("/api/delivery-persons")
@CrossOrigin(origins = "*")
public class DeliveryPersonController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPersonController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryPersonController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new delivery person
     * POST /api/delivery-persons
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<DeliveryPerson>> createDeliveryPerson(@RequestBody DeliveryPerson deliveryPerson) {
        try {
            deliveryPerson.setCreatedAt(LocalDateTime.now());
            deliveryPerson.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DeliveryPerson> response = entityService.create(deliveryPerson);
            logger.info("DeliveryPerson created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating delivery person", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get delivery person by technical UUID
     * GET /api/delivery-persons/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DeliveryPerson>> getDeliveryPersonById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryPerson.ENTITY_NAME).withVersion(DeliveryPerson.ENTITY_VERSION);
            EntityWithMetadata<DeliveryPerson> response = entityService.getById(id, modelSpec, DeliveryPerson.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting delivery person by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get delivery person by business identifier
     * GET /api/delivery-persons/business/{deliveryPersonId}
     */
    @GetMapping("/business/{deliveryPersonId}")
    public ResponseEntity<EntityWithMetadata<DeliveryPerson>> getDeliveryPersonByBusinessId(@PathVariable String deliveryPersonId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryPerson.ENTITY_NAME).withVersion(DeliveryPerson.ENTITY_VERSION);
            EntityWithMetadata<DeliveryPerson> response = entityService.findByBusinessId(
                    modelSpec, deliveryPersonId, "deliveryPersonId", DeliveryPerson.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting delivery person by business ID: {}", deliveryPersonId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update delivery person with optional workflow transition
     * PUT /api/delivery-persons/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<DeliveryPerson>> updateDeliveryPerson(
            @PathVariable UUID id,
            @RequestBody DeliveryPerson deliveryPerson,
            @RequestParam(required = false) String transition) {
        try {
            deliveryPerson.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<DeliveryPerson> response = entityService.update(id, deliveryPerson, transition);
            logger.info("DeliveryPerson updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating delivery person", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete delivery person by technical UUID
     * DELETE /api/delivery-persons/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeliveryPerson(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("DeliveryPerson deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting delivery person", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all delivery persons
     * GET /api/delivery-persons
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<DeliveryPerson>>> getAllDeliveryPersons() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryPerson.ENTITY_NAME).withVersion(DeliveryPerson.ENTITY_VERSION);
            List<EntityWithMetadata<DeliveryPerson>> deliveryPersons = entityService.findAll(modelSpec, DeliveryPerson.class);
            return ResponseEntity.ok(deliveryPersons);
        } catch (Exception e) {
            logger.error("Error getting all delivery persons", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get delivery persons by delivery service
     * GET /api/delivery-persons/service/{deliveryServiceId}
     */
    @GetMapping("/service/{deliveryServiceId}")
    public ResponseEntity<List<EntityWithMetadata<DeliveryPerson>>> getDeliveryPersonsByService(@PathVariable String deliveryServiceId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryPerson.ENTITY_NAME).withVersion(DeliveryPerson.ENTITY_VERSION);

            SimpleCondition serviceCondition = new SimpleCondition()
                    .withJsonPath("$.deliveryServiceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(deliveryServiceId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(serviceCondition));

            List<EntityWithMetadata<DeliveryPerson>> deliveryPersons = entityService.search(modelSpec, condition, DeliveryPerson.class);
            return ResponseEntity.ok(deliveryPersons);
        } catch (Exception e) {
            logger.error("Error getting delivery persons by service: {}", deliveryServiceId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get available delivery persons by delivery service
     * GET /api/delivery-persons/service/{deliveryServiceId}/available
     */
    @GetMapping("/service/{deliveryServiceId}/available")
    public ResponseEntity<List<EntityWithMetadata<DeliveryPerson>>> getAvailableDeliveryPersonsByService(@PathVariable String deliveryServiceId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryPerson.ENTITY_NAME).withVersion(DeliveryPerson.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            conditions.add(new SimpleCondition()
                    .withJsonPath("$.deliveryServiceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(deliveryServiceId)));

            conditions.add(new SimpleCondition()
                    .withJsonPath("$.isAvailable")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true)));

            conditions.add(new SimpleCondition()
                    .withJsonPath("$.isOnline")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true)));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<DeliveryPerson>> deliveryPersons = entityService.search(modelSpec, condition, DeliveryPerson.class);
            return ResponseEntity.ok(deliveryPersons);
        } catch (Exception e) {
            logger.error("Error getting available delivery persons by service: {}", deliveryServiceId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for delivery persons
     * POST /api/delivery-persons/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<DeliveryPerson>>> advancedSearch(
            @RequestBody DeliveryPersonSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DeliveryPerson.ENTITY_NAME).withVersion(DeliveryPerson.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getDeliveryServiceId() != null && !searchRequest.getDeliveryServiceId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.deliveryServiceId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getDeliveryServiceId())));
            }

            if (searchRequest.getVehicleType() != null && !searchRequest.getVehicleType().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.vehicleType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getVehicleType())));
            }

            if (searchRequest.getIsAvailable() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isAvailable")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsAvailable())));
            }

            if (searchRequest.getIsOnline() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isOnline")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsOnline())));
            }

            if (searchRequest.getMinRating() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.rating")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinRating())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<DeliveryPerson>> deliveryPersons = entityService.search(modelSpec, condition, DeliveryPerson.class);
            return ResponseEntity.ok(deliveryPersons);
        } catch (Exception e) {
            logger.error("Error performing advanced delivery person search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced delivery person search requests
     */
    @Getter
    @Setter
    public static class DeliveryPersonSearchRequest {
        private String name;
        private String deliveryServiceId;
        private String vehicleType;
        private Boolean isAvailable;
        private Boolean isOnline;
        private Double minRating;
    }
}
