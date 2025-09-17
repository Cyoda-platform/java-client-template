package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * OwnerController - REST API endpoints for managing pet owners in the Purrfect Pets system
 * 
 * Purpose: Provides CRUD operations and workflow transitions for Owner entities
 * Endpoints: POST /owners, GET /owners/{id}, PUT /owners/{id}, GET /owners
 */
@RestController
@RequestMapping("/owners")
@CrossOrigin(origins = "*")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OwnerController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new owner
     * POST /owners
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Owner>> createOwner(@RequestBody Owner owner) {
        try {
            // Create owner entity
            EntityWithMetadata<Owner> response = entityService.create(owner);
            logger.info("Owner created with ID: {} and ownerId: {}", 
                       response.metadata().getId(), owner.getOwnerId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get owner by technical UUID
     * GET /owners/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Owner.ENTITY_NAME)
                    .withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> response = entityService.getById(id, modelSpec, Owner.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Owner by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get owner by business identifier (ownerId)
     * GET /owners/business/{ownerId}
     */
    @GetMapping("/business/{ownerId}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerByBusinessId(@PathVariable String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Owner.ENTITY_NAME)
                    .withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> response = entityService.findByBusinessId(
                    modelSpec, ownerId, "ownerId", Owner.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Owner by business ID: {}", ownerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update owner with optional workflow transition
     * PUT /owners/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Owner>> updateOwner(
            @PathVariable UUID id,
            @RequestBody UpdateOwnerRequest request) {
        try {
            Owner owner = request.getEntity();
            String transition = request.getTransition();

            // Update owner entity with optional transition
            EntityWithMetadata<Owner> response = entityService.update(id, owner, transition);
            logger.info("Owner updated with ID: {} and transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete owner by technical UUID
     * DELETE /owners/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOwner(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Owner deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all owners
     * GET /owners
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Owner>>> getAllOwners() {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Owner.ENTITY_NAME)
                    .withVersion(Owner.ENTITY_VERSION);
            List<EntityWithMetadata<Owner>> owners = entityService.findAll(modelSpec, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error getting all Owners", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search owners by email
     * GET /owners/search?email=john@example.com
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Owner>>> searchOwnersByEmail(
            @RequestParam String email) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Owner.ENTITY_NAME)
                    .withVersion(Owner.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.email")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(email));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, condition, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error searching Owners by email: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search owners by name
     * GET /owners/search/name?firstName=John&lastName=Smith
     */
    @GetMapping("/search/name")
    public ResponseEntity<List<EntityWithMetadata<Owner>>> searchOwnersByName(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Owner.ENTITY_NAME)
                    .withVersion(Owner.ENTITY_VERSION);

            List<SimpleCondition> conditions = List.of();
            
            if (firstName != null && !firstName.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.firstName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(firstName)));
            }

            if (lastName != null && !lastName.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.lastName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(lastName)));
            }

            if (conditions.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, condition, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error searching Owners by name: firstName={}, lastName={}", firstName, lastName, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for update requests with optional transition
     */
    @Getter
    @Setter
    public static class UpdateOwnerRequest {
        private Owner entity;
        private String transition;
    }
}
