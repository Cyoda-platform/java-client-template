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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OwnerController - Manages owner entities and their workflow transitions
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
            // Set registration date if not provided
            if (owner.getRegistrationDate() == null) {
                owner.setRegistrationDate(LocalDateTime.now());
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
     * Update owner with optional transition
     * PUT /owners/{uuid}
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<Owner>> updateOwner(
            @PathVariable UUID uuid,
            @RequestBody Owner owner,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Owner> response = entityService.update(uuid, owner, transition);
            logger.info("Owner updated with ID: {}", uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get owner by UUID
     * GET /owners/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerById(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> response = entityService.getById(uuid, modelSpec, Owner.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Owner by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get owner by business identifier
     * GET /owners/business/{ownerId}
     */
    @GetMapping("/business/{ownerId}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerByBusinessId(@PathVariable String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
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
     * List all owners
     * GET /owners
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Owner>>> getAllOwners() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            List<EntityWithMetadata<Owner>> owners = entityService.findAll(modelSpec, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error getting all Owners", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Execute specific transition
     * POST /owners/{uuid}/transitions/{transitionName}
     */
    @PostMapping("/{uuid}/transitions/{transitionName}")
    public ResponseEntity<EntityWithMetadata<Owner>> executeTransition(
            @PathVariable UUID uuid,
            @PathVariable String transitionName,
            @RequestBody(required = false) Owner owner) {
        try {
            // If owner data is provided, use it for the update, otherwise get current owner
            Owner ownerToUpdate = owner;
            if (ownerToUpdate == null) {
                ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
                EntityWithMetadata<Owner> currentOwner = entityService.getById(uuid, modelSpec, Owner.class);
                ownerToUpdate = currentOwner.entity();
            }

            EntityWithMetadata<Owner> response = entityService.update(uuid, ownerToUpdate, transitionName);
            logger.info("Owner transition {} executed for ID: {}", transitionName, uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing transition {} for Owner ID: {}", transitionName, uuid, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search owners by verification status
     * GET /owners/search/status?status=verified
     */
    @GetMapping("/search/status")
    public ResponseEntity<List<EntityWithMetadata<Owner>>> searchOwnersByStatus(@RequestParam String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.verificationStatus")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, condition, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error searching Owners by status: {}", status, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for owners
     * POST /owners/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Owner>>> advancedSearch(@RequestBody OwnerSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getHousingType() != null && !searchRequest.getHousingType().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.housingType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getHousingType())));
            }

            if (searchRequest.getHasYard() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.hasYard")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getHasYard())));
            }

            if (searchRequest.getPreferredSpecies() != null && !searchRequest.getPreferredSpecies().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.petPreferences.preferredSpecies")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPreferredSpecies())));
            }

            if (searchRequest.getVerificationStatus() != null && !searchRequest.getVerificationStatus().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.verificationStatus")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getVerificationStatus())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, condition, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error performing advanced owner search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete owner by UUID
     * DELETE /owners/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteOwner(@PathVariable UUID uuid) {
        try {
            entityService.deleteById(uuid);
            logger.info("Owner deleted with ID: {}", uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced owner search requests
     */
    @Getter
    @Setter
    public static class OwnerSearchRequest {
        private String housingType;
        private Boolean hasYard;
        private String preferredSpecies;
        private String verificationStatus;
    }
}
