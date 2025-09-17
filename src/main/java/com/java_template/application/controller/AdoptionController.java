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
 * AdoptionController - Manages adoption entities and their workflow transitions
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

            EntityWithMetadata<Adoption> response = entityService.create(adoption);
            logger.info("Adoption created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update adoption with optional transition
     * PUT /adoptions/{uuid}
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<Adoption>> updateAdoption(
            @PathVariable UUID uuid,
            @RequestBody Adoption adoption,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Adoption> response = entityService.update(uuid, adoption, transition);
            logger.info("Adoption updated with ID: {}", uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get adoption by UUID
     * GET /adoptions/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<Adoption>> getAdoptionById(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> response = entityService.getById(uuid, modelSpec, Adoption.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Adoption by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get adoption by business identifier
     * GET /adoptions/business/{adoptionId}
     */
    @GetMapping("/business/{adoptionId}")
    public ResponseEntity<EntityWithMetadata<Adoption>> getAdoptionByBusinessId(@PathVariable String adoptionId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
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
     * List all adoptions with optional filters
     * GET /adoptions
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> getAllAdoptions() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            List<EntityWithMetadata<Adoption>> adoptions = entityService.findAll(modelSpec, Adoption.class);
            return ResponseEntity.ok(adoptions);
        } catch (Exception e) {
            logger.error("Error getting all Adoptions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Execute specific transition
     * POST /adoptions/{uuid}/transitions/{transitionName}
     */
    @PostMapping("/{uuid}/transitions/{transitionName}")
    public ResponseEntity<EntityWithMetadata<Adoption>> executeTransition(
            @PathVariable UUID uuid,
            @PathVariable String transitionName,
            @RequestBody(required = false) Adoption adoption) {
        try {
            // If adoption data is provided, use it for the update, otherwise get current adoption
            Adoption adoptionToUpdate = adoption;
            if (adoptionToUpdate == null) {
                ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
                EntityWithMetadata<Adoption> currentAdoption = entityService.getById(uuid, modelSpec, Adoption.class);
                adoptionToUpdate = currentAdoption.entity();
            }

            EntityWithMetadata<Adoption> response = entityService.update(uuid, adoptionToUpdate, transitionName);
            logger.info("Adoption transition {} executed for ID: {}", transitionName, uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing transition {} for Adoption ID: {}", transitionName, uuid, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search adoptions by pet ID
     * GET /adoptions/search/pet?petId=PET001
     */
    @GetMapping("/search/pet")
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> searchAdoptionsByPet(@RequestParam String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);

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
            logger.error("Error searching Adoptions by pet ID: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search adoptions by owner ID
     * GET /adoptions/search/owner?ownerId=OWN001
     */
    @GetMapping("/search/owner")
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> searchAdoptionsByOwner(@RequestParam String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);

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
            logger.error("Error searching Adoptions by owner ID: {}", ownerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for adoptions
     * POST /adoptions/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> advancedSearch(@RequestBody AdoptionSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getPetId() != null && !searchRequest.getPetId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.petId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPetId())));
            }

            if (searchRequest.getOwnerId() != null && !searchRequest.getOwnerId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.ownerId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getOwnerId())));
            }

            if (searchRequest.getHomeVisitRequired() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.homeVisitRequired")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getHomeVisitRequired())));
            }

            if (searchRequest.getContractSigned() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.contractSigned")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getContractSigned())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Adoption>> adoptions = entityService.search(modelSpec, condition, Adoption.class);
            return ResponseEntity.ok(adoptions);
        } catch (Exception e) {
            logger.error("Error performing advanced adoption search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete adoption by UUID
     * DELETE /adoptions/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteAdoption(@PathVariable UUID uuid) {
        try {
            entityService.deleteById(uuid);
            logger.info("Adoption deleted with ID: {}", uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced adoption search requests
     */
    @Getter
    @Setter
    public static class AdoptionSearchRequest {
        private String petId;
        private String ownerId;
        private Boolean homeVisitRequired;
        private Boolean contractSigned;
    }
}
