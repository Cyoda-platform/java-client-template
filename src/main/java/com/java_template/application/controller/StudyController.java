package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.study.version_1.Study;
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
 * REST Controller for Study management
 * Handles operational clinical study management
 */
@RestController
@RequestMapping("/ui/studies")
@CrossOrigin(origins = "*")
public class StudyController {

    private static final Logger logger = LoggerFactory.getLogger(StudyController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StudyController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new study
     * POST /ui/studies
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Study>> createStudy(@RequestBody Study study) {
        try {
            // Set creation timestamp and initial status
            study.setCreatedAt(LocalDateTime.now());
            study.setUpdatedAt(LocalDateTime.now());
            
            if (study.getStatus() == null) {
                study.setStatus("setup");
            }
            
            if (study.getCurrentEnrollment() == null) {
                study.setCurrentEnrollment(0);
            }

            EntityWithMetadata<Study> response = entityService.create(study);
            logger.info("Study created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating study", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get study by technical UUID
     * GET /ui/studies/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Study>> getStudyById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);
            EntityWithMetadata<Study> response = entityService.getById(id, modelSpec, Study.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting study by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get study by business identifier
     * GET /ui/studies/business/{studyId}
     */
    @GetMapping("/business/{studyId}")
    public ResponseEntity<EntityWithMetadata<Study>> getStudyByBusinessId(@PathVariable String studyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);
            EntityWithMetadata<Study> response = entityService.findByBusinessId(
                    modelSpec, studyId, "studyId", Study.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting study by business ID: {}", studyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update study with optional workflow transition
     * PUT /ui/studies/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Study>> updateStudy(
            @PathVariable UUID id,
            @RequestBody Study study,
            @RequestParam(required = false) String transition) {
        try {
            study.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Study> response = entityService.update(id, study, transition);
            logger.info("Study updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating study", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete study by technical UUID
     * DELETE /ui/studies/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudy(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Study deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting study", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all studies
     * GET /ui/studies
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Study>>> getAllStudies() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);
            List<EntityWithMetadata<Study>> studies = entityService.findAll(modelSpec, Study.class);
            return ResponseEntity.ok(studies);
        } catch (Exception e) {
            logger.error("Error getting all studies", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search studies by status
     * GET /ui/studies/search?status=active
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Study>>> searchStudiesByStatus(
            @RequestParam String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Study>> studies = entityService.search(modelSpec, condition, Study.class);
            return ResponseEntity.ok(studies);
        } catch (Exception e) {
            logger.error("Error searching studies by status: {}", status, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for studies
     * POST /ui/studies/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Study>>> advancedSearch(
            @RequestBody StudySearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getStatus() != null && !searchRequest.getStatus().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStatus())));
            }

            if (searchRequest.getPhase() != null && !searchRequest.getPhase().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.phase")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPhase())));
            }

            if (searchRequest.getTherapeuticArea() != null && !searchRequest.getTherapeuticArea().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.therapeuticArea")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getTherapeuticArea())));
            }

            if (searchRequest.getSponsorName() != null && !searchRequest.getSponsorName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.sponsorName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSponsorName())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Study>> studies = entityService.search(modelSpec, condition, Study.class);
            return ResponseEntity.ok(studies);
        } catch (Exception e) {
            logger.error("Error performing advanced search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Activate study (workflow transition)
     * POST /ui/studies/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<EntityWithMetadata<Study>> activateStudy(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);
            EntityWithMetadata<Study> current = entityService.getById(id, modelSpec, Study.class);
            
            Study study = current.entity();
            study.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<Study> response = entityService.update(id, study, "activate_study");
            logger.info("Study {} activated", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating study", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Complete study (workflow transition)
     * POST /ui/studies/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<EntityWithMetadata<Study>> completeStudy(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);
            EntityWithMetadata<Study> current = entityService.getById(id, modelSpec, Study.class);
            
            Study study = current.entity();
            study.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<Study> response = entityService.update(id, study, "complete_study");
            logger.info("Study {} completed", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error completing study", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class StudySearchRequest {
        private String status;
        private String phase;
        private String therapeuticArea;
        private String sponsorName;
    }
}
