package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
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
import java.util.List;
import java.util.UUID;

/**
 * CatFactController - REST API controller for cat fact management
 * Base Path: /api/catfacts
 */
@RestController
@RequestMapping("/api/catfacts")
@CrossOrigin(origins = "*")
public class CatFactController {

    private static final Logger logger = LoggerFactory.getLogger(CatFactController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CatFactController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Manually trigger cat fact retrieval from API
     * POST /api/catfacts/retrieve
     * Transition: INITIAL → RETRIEVED (CatFactRetrievalProcessor)
     */
    @PostMapping("/retrieve")
    public ResponseEntity<EntityWithMetadata<CatFact>> retrieveCatFact(@RequestBody RetrieveRequest request) {
        try {
            CatFact catFact = new CatFact();
            catFact.setId(UUID.randomUUID().toString());
            catFact.setSource(request.getSource() != null ? request.getSource() : "catfact.ninja");
            catFact.setRetrievedDate(LocalDateTime.now());
            catFact.setIsUsed(false);
            
            // Set a sample fact if not provided
            if (request.getFact() != null && !request.getFact().trim().isEmpty()) {
                catFact.setFact(request.getFact());
            } else {
                catFact.setFact("Cats have 32 muscles in each ear.");
            }
            catFact.setLength(catFact.getFact().length());

            EntityWithMetadata<CatFact> response = entityService.create(catFact);
            logger.info("CatFact retrieved with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving CatFact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Schedule a cat fact for a campaign
     * PUT /api/catfacts/{id}/schedule
     * Transition: RETRIEVED → SCHEDULED (CatFactSchedulingProcessor)
     */
    @PutMapping("/{id}/schedule")
    public ResponseEntity<EntityWithMetadata<CatFact>> scheduleCatFact(
            @PathVariable UUID id,
            @RequestBody ScheduleRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> current = entityService.getById(id, modelSpec, CatFact.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            CatFact catFact = current.entity();
            catFact.setScheduledDate(request.getScheduledDate());

            EntityWithMetadata<CatFact> response = entityService.update(id, catFact, "transition_to_scheduled");
            logger.info("CatFact {} scheduled for {}", id, request.getScheduledDate());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error scheduling CatFact", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get cat fact details
     * GET /api/catfacts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CatFact>> getCatFact(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> response = entityService.getById(id, modelSpec, CatFact.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting CatFact by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List cat facts with filtering
     * GET /api/catfacts
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> getAllCatFacts(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean isUsed,
            @RequestParam(required = false) String source) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            
            if (isUsed != null) {
                SimpleCondition usedCondition = new SimpleCondition()
                        .withJsonPath("$.isUsed")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(isUsed));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(usedCondition));

                List<EntityWithMetadata<CatFact>> entities = entityService.search(modelSpec, condition, CatFact.class);
                return ResponseEntity.ok(entities);
            } else {
                List<EntityWithMetadata<CatFact>> entities = entityService.findAll(modelSpec, CatFact.class);
                return ResponseEntity.ok(entities);
            }
        } catch (Exception e) {
            logger.error("Error getting all CatFacts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get available cat facts for scheduling (RETRIEVED state)
     * GET /api/catfacts/available
     */
    @GetMapping("/available")
    public ResponseEntity<List<EntityWithMetadata<CatFact>>> getAvailableCatFacts() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            
            SimpleCondition usedCondition = new SimpleCondition()
                    .withJsonPath("$.isUsed")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(false));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(usedCondition));

            List<EntityWithMetadata<CatFact>> entities = entityService.search(modelSpec, condition, CatFact.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting available CatFacts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class RetrieveRequest {
        private String source = "catfact.ninja";
        private String fact; // Optional - for testing purposes
        private String transitionName = "retrieve";
    }

    @Getter
    @Setter
    public static class ScheduleRequest {
        private LocalDateTime scheduledDate;
        private String transitionName = "schedule";
    }
}
