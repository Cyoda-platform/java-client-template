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
 * AdoptionController - REST endpoints for adoption operations
 * 
 * Manages adoption application submission, approval, completion, and cancellation
 * through thin proxy methods to EntityService.
 */
@RestController
@RequestMapping("/api/adoptions")
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
     * Submit a new adoption application
     * POST /api/adoptions
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Adoption>> createAdoption(@RequestBody Adoption adoption) {
        try {
            // Set application date if not provided
            if (adoption.getApplicationDate() == null) {
                adoption.setApplicationDate(LocalDate.now());
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
     * Get adoption by technical UUID
     * GET /api/adoptions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Adoption>> getAdoptionById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> response = entityService.getById(id, modelSpec, Adoption.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Adoption by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Search adoptions with filtering options
     * GET /api/adoptions?petId=pet-123&ownerId=owner-456&state=pending
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Adoption>>> searchAdoptions(
            @RequestParam(required = false) String petId,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String state) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            
            List<QueryCondition> conditions = new ArrayList<>();
            
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

            List<EntityWithMetadata<Adoption>> adoptions;
            if (conditions.isEmpty()) {
                adoptions = entityService.findAll(modelSpec, Adoption.class);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                adoptions = entityService.search(modelSpec, condition, Adoption.class);
            }

            // Filter by state if provided (state is in metadata, not entity)
            if (state != null && !state.trim().isEmpty()) {
                adoptions = adoptions.stream()
                        .filter(adoption -> state.equals(adoption.metadata().getState()))
                        .toList();
            }

            return ResponseEntity.ok(adoptions);
        } catch (Exception e) {
            logger.error("Error searching adoptions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Approve an adoption application
     * POST /api/adoptions/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<EntityWithMetadata<Adoption>> approveAdoption(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "approve_adoption";
            
            // Get current adoption to update
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> currentAdoption = entityService.getById(id, modelSpec, Adoption.class);
            
            EntityWithMetadata<Adoption> response = entityService.update(id, currentAdoption.entity(), transition);
            logger.info("Adoption approved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error approving Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Complete the adoption process
     * POST /api/adoptions/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<EntityWithMetadata<Adoption>> completeAdoption(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "complete_adoption";
            
            // Get current adoption to update
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> currentAdoption = entityService.getById(id, modelSpec, Adoption.class);
            
            EntityWithMetadata<Adoption> response = entityService.update(id, currentAdoption.entity(), transition);
            logger.info("Adoption completed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error completing Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel an adoption application
     * POST /api/adoptions/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<EntityWithMetadata<Adoption>> cancelAdoption(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "cancel_adoption";
            
            // Get current adoption to update
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> currentAdoption = entityService.getById(id, modelSpec, Adoption.class);
            
            EntityWithMetadata<Adoption> response = entityService.update(id, currentAdoption.entity(), transition);
            logger.info("Adoption cancelled with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Process pet return after adoption
     * POST /api/adoptions/{id}/return
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<EntityWithMetadata<Adoption>> processReturn(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "process_return";
            
            // Get current adoption to update
            ModelSpec modelSpec = new ModelSpec().withName(Adoption.ENTITY_NAME).withVersion(Adoption.ENTITY_VERSION);
            EntityWithMetadata<Adoption> currentAdoption = entityService.getById(id, modelSpec, Adoption.class);
            
            // Set return reason if provided
            if (request != null && request.getDetails() != null) {
                currentAdoption.entity().setReturnReason(request.getDetails());
            }
            
            EntityWithMetadata<Adoption> response = entityService.update(id, currentAdoption.entity(), transition);
            logger.info("Pet return processed for adoption ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing return for Adoption", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update adoption details
     * PUT /api/adoptions/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Adoption>> updateAdoption(
            @PathVariable UUID id,
            @RequestBody Adoption adoption,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Adoption> response = entityService.update(id, adoption, transition);
            logger.info("Adoption updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Adoption", e);
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
