package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet_entity.version_1.PetEntity;
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
 * PetController - REST controller for Pet entity operations
 * Base Path: /api/pets
 */
@RestController
@RequestMapping("/api/pets")
@CrossOrigin(origins = "*")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new pet entity
     * POST /api/pets
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<PetEntity>> createPet(@RequestBody PetEntity entity) {
        try {
            // Set creation timestamp
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            // Initialize default values
            if (entity.getSalesVolume() == null) {
                entity.setSalesVolume(0);
            }
            if (entity.getRevenue() == null) {
                entity.setRevenue(0.0);
            }

            EntityWithMetadata<PetEntity> response = entityService.create(entity);
            logger.info("Pet created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get pet by technical UUID
     * GET /api/pets/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<PetEntity>> getPetById(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetEntity.ENTITY_NAME).withVersion(PetEntity.ENTITY_VERSION);
            EntityWithMetadata<PetEntity> response = entityService.getById(uuid, modelSpec, PetEntity.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Pet by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update pet entity with optional state transition
     * PUT /api/pets/{uuid}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<PetEntity>> updatePet(
            @PathVariable UUID uuid,
            @RequestBody PetEntity entity,
            @RequestParam(required = false) String transitionName) {
        try {
            // Set update timestamp
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<PetEntity> response = entityService.update(uuid, entity, transitionName);
            logger.info("Pet updated with ID: {}", uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete pet entity
     * DELETE /api/pets/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deletePet(@PathVariable UUID uuid) {
        try {
            entityService.deleteById(uuid);
            logger.info("Pet deleted with ID: {}", uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all pets with optional filtering
     * GET /api/pets?status=available&category=Dogs
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<PetEntity>>> getAllPets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetEntity.ENTITY_NAME).withVersion(PetEntity.ENTITY_VERSION);

            // Build search conditions
            List<QueryCondition> conditions = new ArrayList<>();

            if (category != null && !category.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.category.name")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(category)));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<PetEntity>> entities = entityService.search(modelSpec, condition, PetEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all Pets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search pets by performance criteria
     * POST /api/pets/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<PetEntity>>> searchPets(@RequestBody PetSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(PetEntity.ENTITY_NAME).withVersion(PetEntity.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getMinPrice() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinPrice())));
            }

            if (searchRequest.getMaxPrice() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxPrice())));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<PetEntity>> entities = entityService.search(modelSpec, condition, PetEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching Pets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for pet search requests
     */
    @Getter
    @Setter
    public static class PetSearchRequest {
        private String name;
        private String category;
        private Double minPrice;
        private Double maxPrice;
        private Integer minSalesVolume;
        private Integer maxSalesVolume;
    }
}
