package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoption.version_1.Adoption;
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
 * AdoptionController - REST API endpoints for managing pet adoptions in the Purrfect Pets system
 * 
 * Purpose: Provides CRUD operations and workflow transitions for Adoption entities
 * Endpoints: POST /adoptions, GET /adoptions/{id}, PUT /adoptions/{id}, GET /adoptions
 */
@RestController
@RequestMapping("/adoptions")
@CrossOrigin(origins = "*")
public class AdoptionController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new adoption application
     * POST /adoptions
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Adoption>> createAdoption(@RequestBody Adoption adoption) {
        try {
            // Set application date if not provided
            if (adoption.getApplicationDate() == null) {
                adoption.setApplicationDate(LocalDateTime.now());
            }

            // Create adoption entity
            EntityWithMetadata<Adoption> response = entityService.create(adoption);
            logger.info("Adoption created with ID: {} and adoptionId: {}", 
                       response.metadata().getId(), adoption.getAdoptionId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get adoption by technical UUID
     * GET /adoptions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Adoption>> getAdoptionById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Adoption.ENTITY_NAME)
                    .withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> response = entityService.getById(id, modelSpec, Adoption.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Adoption by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get adoption by business identifier (adoptionId)
     * GET /adoptions/business/{adoptionId}
     */
    @GetMapping("/business/{adoptionId}")
    public ResponseEntity<EntityWithMetadata<Adoption>> getAdoptionByBusinessId(@PathVariable String adoptionId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Adoption.ENTITY_NAME)
                    .withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> response = entityService.findByBusinessId(
                    modelSpec, adoptionId, "adoptionId", Adoption.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Adoption by business ID: {}", adoptionId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update adoption with optional workflow transition
     * PUT /adoptions/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Adoption>> updateAdoption(
            @PathVariable UUID id,
            @RequestBody UpdateAdoptionRequest request) {
        try {
            Adoption adoption = request.getEntity();
            String transition = request.getTransition();

            // Set approval date if approving
            if ("approve_adoption".equals(transition) && adoption.getApprovalDate() == null) {
                adoption.setApprovalDate(LocalDateTime.now());
            }

            // Update adoption entity with optional transition
            EntityWithMetadata<Adoption> response = entityService.update(id, adoption, transition);
            logger.info("Adoption updated with ID: {} and transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete adoption by technical UUID
     * DELETE /adoptions/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdoption(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Adoption deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all adoptions with optional filtering
     * GET /adoptions?petId=PET001&ownerId=OWN001
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> getAllAdoptions(
            @RequestParam(required = false) String petId,
            @RequestParam(required = false) String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Adoption.ENTITY_NAME)
                    .withVersion(Adoption.ENTITY_VERSION);

            // If no filters, return all adoptions
            if (petId == null && ownerId == null) {
                List<EntityWithMetadata<Adoption>> adoptions = entityService.findAll(modelSpec, Adoption.class);
                return ResponseEntity.ok(adoptions);
            }

            // Build search conditions for filtering
            List<SimpleCondition> conditions = new ArrayList<>();

            if (petId != null && !petId.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.petId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(petId)));
            }

            if (ownerId != null && !ownerId.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.ownerId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(ownerId)));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Adoption>> adoptions = entityService.search(modelSpec, condition, Adoption.class);
            return ResponseEntity.ok(adoptions);
        } catch (Exception e) {
            logger.error("Error getting filtered Adoptions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get adoptions for specific pet
     * GET /adoptions/by-pet/{petId}
     */
    @GetMapping("/by-pet/{petId}")
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> getAdoptionsByPet(@PathVariable String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Adoption.ENTITY_NAME)
                    .withVersion(Adoption.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Adoption>> adoptions = entityService.search(modelSpec, condition, Adoption.class);
            return ResponseEntity.ok(adoptions);
        } catch (Exception e) {
            logger.error("Error getting Adoptions by pet: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get adoptions for specific owner
     * GET /adoptions/by-owner/{ownerId}
     */
    @GetMapping("/by-owner/{ownerId}")
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> getAdoptionsByOwner(@PathVariable String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Adoption.ENTITY_NAME)
                    .withVersion(Adoption.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.ownerId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(ownerId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Adoption>> adoptions = entityService.search(modelSpec, condition, Adoption.class);
            return ResponseEntity.ok(adoptions);
        } catch (Exception e) {
            logger.error("Error getting Adoptions by owner: {}", ownerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for update requests with optional transition
     */
    @Getter
    @Setter
    public static class UpdateAdoptionRequest {
        private Adoption entity;
        private String transition;
    }
}
