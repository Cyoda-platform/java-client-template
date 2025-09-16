package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.eggalarm.version_1.EggAlarm;
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
 * EggAlarmController - REST controller for managing egg alarm operations
 * 
 * Provides endpoints for creating, starting, cancelling, and retrieving egg alarms.
 * All endpoints follow the thin proxy pattern with no embedded business logic.
 */
@RestController
@RequestMapping("/api/egg-alarms")
@CrossOrigin(origins = "*")
public class EggAlarmController {

    private static final Logger logger = LoggerFactory.getLogger(EggAlarmController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EggAlarmController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new egg alarm
     * POST /api/egg-alarms
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EggAlarm>> createEggAlarm(@RequestBody CreateEggAlarmRequest request) {
        try {
            // Create EggAlarm entity from request
            EggAlarm eggAlarm = new EggAlarm();
            eggAlarm.setId(UUID.randomUUID().toString()); // Generate business ID
            eggAlarm.setEggType(request.getEggType());
            eggAlarm.setCreatedAt(LocalDateTime.now());
            eggAlarm.setUserId(request.getUserId());

            // Create entity via EntityService - automatic transition to CREATED
            EntityWithMetadata<EggAlarm> response = entityService.create(eggAlarm);
            logger.info("EggAlarm created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating EggAlarm", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Start an existing egg alarm timer
     * PUT /api/egg-alarms/{id}/start
     */
    @PutMapping("/{id}/start")
    public ResponseEntity<EntityWithMetadata<EggAlarm>> startEggAlarm(@PathVariable UUID id) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(EggAlarm.ENTITY_NAME).withVersion(EggAlarm.ENTITY_VERSION);
            EntityWithMetadata<EggAlarm> currentEntity = entityService.getById(id, modelSpec, EggAlarm.class);
            
            // Update with manual transition to start the alarm
            EntityWithMetadata<EggAlarm> response = entityService.update(id, currentEntity.entity(), "transition_to_active");
            logger.info("EggAlarm started with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error starting EggAlarm", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel an egg alarm
     * PUT /api/egg-alarms/{id}/cancel
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<EntityWithMetadata<EggAlarm>> cancelEggAlarm(@PathVariable UUID id) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(EggAlarm.ENTITY_NAME).withVersion(EggAlarm.ENTITY_VERSION);
            EntityWithMetadata<EggAlarm> currentEntity = entityService.getById(id, modelSpec, EggAlarm.class);
            
            // Update with manual transition to cancel the alarm
            EntityWithMetadata<EggAlarm> response = entityService.update(id, currentEntity.entity(), "transition_to_cancelled");
            logger.info("EggAlarm cancelled with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling EggAlarm", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get egg alarm status by technical UUID
     * GET /api/egg-alarms/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EggAlarm>> getEggAlarmById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EggAlarm.ENTITY_NAME).withVersion(EggAlarm.ENTITY_VERSION);
            EntityWithMetadata<EggAlarm> response = entityService.getById(id, modelSpec, EggAlarm.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EggAlarm by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List user's egg alarms with optional filtering
     * GET /api/egg-alarms
     */
    @GetMapping
    public ResponseEntity<EggAlarmListResponse> listEggAlarms(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EggAlarm.ENTITY_NAME).withVersion(EggAlarm.ENTITY_VERSION);
            
            List<EntityWithMetadata<EggAlarm>> alarms;
            
            if (userId != null || state != null) {
                // Build search conditions
                List<SimpleCondition> conditions = new ArrayList<>();
                
                if (userId != null && !userId.trim().isEmpty()) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.userId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(userId)));
                }
                
                if (state != null && !state.trim().isEmpty()) {
                    // Note: state filtering would need to be done on metadata, which is more complex
                    // For now, we'll search all and filter in memory (not optimal for large datasets)
                    logger.warn("State filtering not implemented in search - will filter all results");
                }
                
                if (!conditions.isEmpty()) {
                    GroupCondition condition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    alarms = entityService.search(modelSpec, condition, EggAlarm.class);
                } else {
                    alarms = entityService.findAll(modelSpec, EggAlarm.class);
                }
            } else {
                alarms = entityService.findAll(modelSpec, EggAlarm.class);
            }
            
            // Filter by state if specified (in-memory filtering)
            if (state != null && !state.trim().isEmpty()) {
                alarms = alarms.stream()
                        .filter(alarm -> state.equals(alarm.metadata().getState()))
                        .toList();
            }
            
            // Apply pagination (simple in-memory pagination)
            int totalCount = alarms.size();
            int startIndex = Math.min(offset, totalCount);
            int endIndex = Math.min(offset + limit, totalCount);
            List<EntityWithMetadata<EggAlarm>> paginatedAlarms = alarms.subList(startIndex, endIndex);
            
            EggAlarmListResponse response = new EggAlarmListResponse();
            response.setAlarms(paginatedAlarms);
            response.setTotalCount(totalCount);
            response.setLimit(limit);
            response.setOffset(offset);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error listing EggAlarms", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request/Response DTOs

    /**
     * DTO for creating egg alarm requests
     */
    @Getter
    @Setter
    public static class CreateEggAlarmRequest {
        private String eggType; // SOFT_BOILED, MEDIUM_BOILED, HARD_BOILED
        private String userId;
    }

    /**
     * DTO for listing egg alarms response
     */
    @Getter
    @Setter
    public static class EggAlarmListResponse {
        private List<EntityWithMetadata<EggAlarm>> alarms;
        private Integer totalCount;
        private Integer limit;
        private Integer offset;
    }
}
