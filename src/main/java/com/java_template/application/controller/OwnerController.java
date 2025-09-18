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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OwnerController - REST endpoints for owner operations
 * 
 * Manages owner registration, verification, and profile management
 * through thin proxy methods to EntityService.
 */
@RestController
@RequestMapping("/api/owners")
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
     * Register a new owner
     * POST /api/owners
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Owner>> createOwner(@RequestBody Owner owner) {
        try {
            // Set registration date if not provided
            if (owner.getRegistrationDate() == null) {
                owner.setRegistrationDate(LocalDate.now());
            }

            EntityWithMetadata<Owner> response = entityService.create(owner);
            logger.info("Owner created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get owner by technical UUID
     * GET /api/owners/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> response = entityService.getById(id, modelSpec, Owner.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Owner by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get owner by email (business identifier)
     * GET /api/owners/email/{email}
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerByEmail(@PathVariable String email) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> response = entityService.findByBusinessId(
                    modelSpec, email, "email", Owner.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Owner by email: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search owners with filtering options
     * GET /api/owners?housingType=house&state=verified
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Owner>>> searchOwners(
            @RequestParam(required = false) String housingType,
            @RequestParam(required = false) String state) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            
            List<QueryCondition> conditions = new ArrayList<>();
            
            if (housingType != null && !housingType.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.housingType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(housingType)));
            }

            List<EntityWithMetadata<Owner>> owners;
            if (conditions.isEmpty()) {
                owners = entityService.findAll(modelSpec, Owner.class);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                owners = entityService.search(modelSpec, condition, Owner.class);
            }

            // Filter by state if provided (state is in metadata, not entity)
            if (state != null && !state.trim().isEmpty()) {
                owners = owners.stream()
                        .filter(owner -> state.equals(owner.metadata().getState()))
                        .toList();
            }

            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error searching owners", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Verify owner for pet adoption
     * POST /api/owners/{id}/verify
     */
    @PostMapping("/{id}/verify")
    public ResponseEntity<EntityWithMetadata<Owner>> verifyOwner(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "verify_owner";
            
            // Get current owner to update
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> currentOwner = entityService.getById(id, modelSpec, Owner.class);
            
            EntityWithMetadata<Owner> response = entityService.update(id, currentOwner.entity(), transition);
            logger.info("Owner verified with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error verifying Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Suspend owner account
     * POST /api/owners/{id}/suspend
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<EntityWithMetadata<Owner>> suspendOwner(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "suspend_owner";
            
            // Get current owner to update
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> currentOwner = entityService.getById(id, modelSpec, Owner.class);
            
            EntityWithMetadata<Owner> response = entityService.update(id, currentOwner.entity(), transition);
            logger.info("Owner suspended with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error suspending Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reactivate suspended owner account
     * POST /api/owners/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Owner>> reactivateOwner(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "reactivate_owner";
            
            // Get current owner to update
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> currentOwner = entityService.getById(id, modelSpec, Owner.class);
            
            EntityWithMetadata<Owner> response = entityService.update(id, currentOwner.entity(), transition);
            logger.info("Owner reactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reactivating Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update owner profile
     * PUT /api/owners/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Owner>> updateOwner(
            @PathVariable UUID id,
            @RequestBody Owner owner,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Owner> response = entityService.update(id, owner, transition);
            logger.info("Owner updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for transition requests
     */
    @Getter
    @Setter
    public static class TransitionRequest {
        private String transitionName;
        private String details;
    }
}
