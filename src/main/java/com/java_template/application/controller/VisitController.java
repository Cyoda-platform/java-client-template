package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.visit.version_1.Visit;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Visit management
 * Handles clinical trial visit scheduling, completion, and management
 */
@RestController
@RequestMapping("/ui/visits")
@CrossOrigin(origins = "*")
public class VisitController {

    private static final Logger logger = LoggerFactory.getLogger(VisitController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public VisitController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new visit
     * POST /ui/visits
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Visit>> createVisit(@RequestBody Visit visit) {
        try {
            // Set creation timestamp and initial status
            visit.setCreatedAt(LocalDateTime.now());
            visit.setUpdatedAt(LocalDateTime.now());
            
            if (visit.getStatus() == null) {
                visit.setStatus("planned");
            }
            
            if (visit.getLocked() == null) {
                visit.setLocked(false);
            }

            EntityWithMetadata<Visit> response = entityService.create(visit);
            logger.info("Visit created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating visit", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get visit by technical UUID
     * GET /ui/visits/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Visit>> getVisitById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);
            EntityWithMetadata<Visit> response = entityService.getById(id, modelSpec, Visit.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting visit by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get visit by business identifier
     * GET /ui/visits/business/{visitId}
     */
    @GetMapping("/business/{visitId}")
    public ResponseEntity<EntityWithMetadata<Visit>> getVisitByBusinessId(@PathVariable String visitId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);
            EntityWithMetadata<Visit> response = entityService.findByBusinessId(
                    modelSpec, visitId, "visitId", Visit.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting visit by business ID: {}", visitId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update visit with optional workflow transition
     * PUT /ui/visits/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Visit>> updateVisit(
            @PathVariable UUID id,
            @RequestBody Visit visit,
            @RequestParam(required = false) String transition) {
        try {
            visit.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Visit> response = entityService.update(id, visit, transition);
            logger.info("Visit updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating visit", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete visit
     * DELETE /ui/visits/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVisit(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Visit deleted with ID: {}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error deleting visit", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all visits
     * GET /ui/visits
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Visit>>> getAllVisits() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);
            List<EntityWithMetadata<Visit>> visits = entityService.findAll(modelSpec, Visit.class);
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            logger.error("Error getting all visits", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get visits by subject
     * GET /ui/visits/subject/{subjectId}
     */
    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<EntityWithMetadata<Visit>>> getVisitsBySubject(@PathVariable String subjectId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.subjectId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(subjectId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Visit>> visits = entityService.search(modelSpec, condition, Visit.class);
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            logger.error("Error getting visits by subject: {}", subjectId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get visits by study
     * GET /ui/visits/study/{studyId}
     */
    @GetMapping("/study/{studyId}")
    public ResponseEntity<List<EntityWithMetadata<Visit>>> getVisitsByStudy(@PathVariable String studyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.studyId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(studyId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Visit>> visits = entityService.search(modelSpec, condition, Visit.class);
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            logger.error("Error getting visits by study: {}", studyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search visits by status
     * GET /ui/visits/search?status=completed
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Visit>>> searchVisitsByStatus(
            @RequestParam String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Visit>> visits = entityService.search(modelSpec, condition, Visit.class);
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            logger.error("Error searching visits by status: {}", status, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for visits
     * POST /ui/visits/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Visit>>> advancedSearch(
            @RequestBody VisitSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getStudyId() != null && !searchRequest.getStudyId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.studyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStudyId())));
            }

            if (searchRequest.getSubjectId() != null && !searchRequest.getSubjectId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.subjectId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSubjectId())));
            }

            if (searchRequest.getStatus() != null && !searchRequest.getStatus().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStatus())));
            }

            if (searchRequest.getVisitCode() != null && !searchRequest.getVisitCode().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.visitCode")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getVisitCode())));
            }

            if (searchRequest.getPlannedDateFrom() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.plannedDate")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getPlannedDateFrom())));
            }

            if (searchRequest.getPlannedDateTo() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.plannedDate")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getPlannedDateTo())));
            }

            if (searchRequest.getLocked() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.locked")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getLocked())));
            }

            if (conditions.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Visit>> visits = entityService.search(modelSpec, condition, Visit.class);
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            logger.error("Error in advanced visit search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Complete visit (workflow transition)
     * POST /ui/visits/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<EntityWithMetadata<Visit>> completeVisit(
            @PathVariable UUID id,
            @RequestBody VisitCompletionRequest completionRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);
            EntityWithMetadata<Visit> current = entityService.getById(id, modelSpec, Visit.class);

            Visit visit = current.entity();

            // Validate visit can be completed using enhanced validation
            try {
                visit.validateForOperation("complete");
            } catch (Exception e) {
                logger.warn("Cannot complete visit {}: {}", id, e.getMessage());
                return ResponseEntity.badRequest().build();
            }

            // Update visit with completion data
            visit.setActualDate(completionRequest.getActualDate());
            visit.setStatus("completed");
            visit.setCompletedAt(LocalDateTime.now());
            visit.setCompletedBy(completionRequest.getCompletedBy());
            visit.setCompletionNotes(completionRequest.getCompletionNotes());
            visit.setUpdatedAt(LocalDateTime.now());

            // Update CRF data if provided
            if (completionRequest.getCrfData() != null) {
                visit.setCrfData(completionRequest.getCrfData());
            }

            // Add deviations if provided
            if (completionRequest.getDeviations() != null && !completionRequest.getDeviations().isEmpty()) {
                List<Visit.Deviation> existingDeviations = visit.getDeviations();
                if (existingDeviations == null) {
                    existingDeviations = new ArrayList<>();
                }
                existingDeviations.addAll(completionRequest.getDeviations());
                visit.setDeviations(existingDeviations);
            }

            EntityWithMetadata<Visit> response = entityService.update(id, visit, "complete_visit");
            logger.info("Visit {} completed", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error completing visit", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Schedule visit (workflow transition)
     * POST /ui/visits/{id}/schedule
     */
    @PostMapping("/{id}/schedule")
    public ResponseEntity<EntityWithMetadata<Visit>> scheduleVisit(
            @PathVariable UUID id,
            @RequestBody VisitSchedulingRequest schedulingRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);
            EntityWithMetadata<Visit> current = entityService.getById(id, modelSpec, Visit.class);

            Visit visit = current.entity();

            // Update visit with scheduling data
            visit.setPlannedDate(schedulingRequest.getPlannedDate());
            visit.setWindowMinusDays(schedulingRequest.getWindowMinusDays());
            visit.setWindowPlusDays(schedulingRequest.getWindowPlusDays());
            visit.setMandatoryProcedures(schedulingRequest.getMandatoryProcedures());
            visit.setUpdatedAt(LocalDateTime.now());

            if (schedulingRequest.getStatus() != null) {
                visit.setStatus(schedulingRequest.getStatus());
            }

            EntityWithMetadata<Visit> response = entityService.update(id, visit, "schedule_visit");
            logger.info("Visit {} scheduled for {}", id, schedulingRequest.getPlannedDate());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error scheduling visit", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Lock visit data
     * POST /ui/visits/{id}/lock
     */
    @PostMapping("/{id}/lock")
    public ResponseEntity<EntityWithMetadata<Visit>> lockVisit(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);
            EntityWithMetadata<Visit> current = entityService.getById(id, modelSpec, Visit.class);

            Visit visit = current.entity();

            // Validate visit can be locked using enhanced validation
            try {
                visit.validateForOperation("lock");
            } catch (Exception e) {
                logger.warn("Cannot lock visit {}: {}", id, e.getMessage());
                return ResponseEntity.badRequest().build();
            }

            visit.setLocked(true);
            visit.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Visit> response = entityService.update(id, visit, null);
            logger.info("Visit {} locked", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error locking visit", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Unlock visit data
     * POST /ui/visits/{id}/unlock
     */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<EntityWithMetadata<Visit>> unlockVisit(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Visit.ENTITY_NAME).withVersion(Visit.ENTITY_VERSION);
            EntityWithMetadata<Visit> current = entityService.getById(id, modelSpec, Visit.class);

            Visit visit = current.entity();
            visit.setLocked(false);
            visit.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Visit> response = entityService.update(id, visit, null);
            logger.info("Visit {} unlocked", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error unlocking visit", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for visit search requests
     */
    @Getter
    @Setter
    public static class VisitSearchRequest {
        private String studyId;
        private String subjectId;
        private String status;
        private String visitCode;
        private LocalDate plannedDateFrom;
        private LocalDate plannedDateTo;
        private Boolean locked;
    }

    /**
     * DTO for visit completion requests
     */
    @Getter
    @Setter
    public static class VisitCompletionRequest {
        private LocalDate actualDate;
        private String completedBy;
        private String completionNotes;
        private JsonNode crfData;
        private List<Visit.Deviation> deviations;
    }

    /**
     * DTO for visit scheduling requests
     */
    @Getter
    @Setter
    public static class VisitSchedulingRequest {
        private LocalDate plannedDate;
        private Integer windowMinusDays;
        private Integer windowPlusDays;
        private List<String> mandatoryProcedures;
        private String status;
    }
}
