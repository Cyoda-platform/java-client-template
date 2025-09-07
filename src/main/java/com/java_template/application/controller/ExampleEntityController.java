package com.java_template.application.controller;

import com.java_template.application.entity.example_entity.version_1.ExampleEntity;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Golden Example Controller - Template for creating new controllers
 * <p>
 * This is a generified example controller that demonstrates:
 * - Proper REST controller implementation
 * - EntityService integration patterns
 * - CRUD operations with EntityWithMetadata
 * - Performance-optimized EntityService methods
 * - Error handling and logging
 * - Request/Response DTOs
 * <p>
 * To create a new controller:
 * 1. Copy this file to your controller package
 * 2. Rename class from ExampleEntityController to YourEntityController
 * 3. Update entity type from ExampleEntity to your entity
 * 4. Update @RequestMapping path from "/ui/example" to your path
 * 5. Update ModelSpec entity name from "ExampleEntity" to your entity name
 * 6. Update business ID field name in findByBusinessId calls
 * 7. Add custom endpoints as needed
 * 8. Update request/response DTOs for your specific use cases
 */
@RestController
@RequestMapping("/ui/example")
@CrossOrigin(origins = "*")
public class ExampleEntityController {

    private static final Logger logger = LoggerFactory.getLogger(ExampleEntityController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ExampleEntityController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new entity
     * POST /ui/example
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<ExampleEntity>> createEntity(@RequestBody ExampleEntity entity) {
        try {
            // Set creation timestamp
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            // CRITICAL: Pass entity directly - it IS the payload
            EntityWithMetadata<ExampleEntity> response = entityService.create(entity);
            logger.info("ExampleEntity created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating ExampleEntity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get entity by technical UUID (FASTEST method)
     * GET /ui/example/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ExampleEntity>> getEntityById(@PathVariable UUID id) {
        try {
            // Use getById() for technical UUID (FASTEST)
            ModelSpec modelSpec = new ModelSpec().withName(ExampleEntity.ENTITY_NAME).withVersion(ExampleEntity.ENTITY_VERSION);
            EntityWithMetadata<ExampleEntity> response = entityService.getById(id, modelSpec, ExampleEntity.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting ExampleEntity by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get entity by business identifier (MEDIUM SPEED)
     * GET /ui/example/business/{exampleId}
     */
    @GetMapping("/business/{exampleId}")
    public ResponseEntity<EntityWithMetadata<ExampleEntity>> getEntityByBusinessId(@PathVariable String exampleId) {
        try {
            // Use findByBusinessId() for business identifiers (MEDIUM SPEED)
            ModelSpec modelSpec = new ModelSpec().withName(ExampleEntity.ENTITY_NAME).withVersion(ExampleEntity.ENTITY_VERSION);
            EntityWithMetadata<ExampleEntity> response = entityService.findByBusinessId(
                    modelSpec, exampleId, "exampleId", ExampleEntity.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting ExampleEntity by business ID: {}", exampleId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update entity with optional workflow transition
     * PUT /ui/example/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ExampleEntity>> updateEntity(
            @PathVariable UUID id,
            @RequestBody ExampleEntity entity,
            @RequestParam(required = false) String transition) {
        try {
            // Set update timestamp
            entity.setUpdatedAt(LocalDateTime.now());

            // CRITICAL: Pass entity directly - no payload manipulation needed
            EntityWithMetadata<ExampleEntity> response = entityService.update(id, entity, transition);
            logger.info("ExampleEntity updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating ExampleEntity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete entity by technical UUID
     * DELETE /ui/example/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("ExampleEntity deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting ExampleEntity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all entities (USE SPARINGLY - can be slow for large datasets)
     * GET /ui/example
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<ExampleEntity>>> getAllEntities() {
        try {
            // Use findAll() sparingly - can be slow for large datasets
            ModelSpec modelSpec = new ModelSpec().withName(ExampleEntity.ENTITY_NAME).withVersion(ExampleEntity.ENTITY_VERSION);
            List<EntityWithMetadata<ExampleEntity>> entities = entityService.findAll(modelSpec, ExampleEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all ExampleEntities", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search entities by description (example of search functionality)
     * GET /ui/example/search?description=text
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<ExampleEntity>>> searchEntitiesByDescription(
            @RequestParam String description) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ExampleEntity.ENTITY_NAME).withVersion(ExampleEntity.ENTITY_VERSION);

            // Create search condition using generated classes directly
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.description")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(description));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<ExampleEntity>> entities = entityService.search(modelSpec, condition, ExampleEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching ExampleEntities by description: {}", description, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get entities by name pattern (example of complex search)
     * POST /ui/example/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<ExampleEntity>>> advancedSearch(
            @RequestBody SearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ExampleEntity.ENTITY_NAME).withVersion(ExampleEntity.ENTITY_VERSION);

            // Build complex search condition using generated classes directly
            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getMinAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.amount")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinAmount())));
            }

            if (searchRequest.getMaxAmount() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.amount")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxAmount())));
            }

            // Create GroupCondition with all conditions
            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));
            List<EntityWithMetadata<ExampleEntity>> entities = entityService.search(modelSpec, condition, ExampleEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error performing advanced search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs for specific operations

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class SearchRequest {
        private String name;
        private Double minAmount;
        private Double maxAmount;
    }
}