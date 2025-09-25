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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Study entity
 * 
 * Provides CRUD operations and specialized endpoints for clinical study management.
 */
@RestController
@RequestMapping("/ui/study")
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
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Study>> createStudy(@RequestBody Study study) {
        logger.info("Creating new study: {}", study.getStudyId());
        
        try {
            EntityWithMetadata<Study> savedStudy = entityService.save(study, Study.class);
            logger.info("Successfully created study with technical ID: {}", savedStudy.metadata().getId());
            return ResponseEntity.ok(savedStudy);
        } catch (Exception e) {
            logger.error("Error creating study: {}", study.getStudyId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get study by technical ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Study>> getStudyById(@PathVariable UUID id) {
        logger.info("Retrieving study by technical ID: {}", id);
        
        try {
            EntityWithMetadata<Study> study = entityService.findById(createModelSpec(), id, Study.class);
            if (study != null) {
                return ResponseEntity.ok(study);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving study by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get study by business ID (studyId)
     */
    @GetMapping("/business/{studyId}")
    public ResponseEntity<EntityWithMetadata<Study>> getStudyByBusinessId(@PathVariable String studyId) {
        logger.info("Retrieving study by business ID: {}", studyId);
        
        try {
            EntityWithMetadata<Study> study = entityService.findByBusinessId(
                createModelSpec(), 
                "studyId", 
                studyId, 
                Study.class
            );
            if (study != null) {
                return ResponseEntity.ok(study);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving study by business ID: {}", studyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update study
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Study>> updateStudy(
            @PathVariable UUID id, 
            @RequestBody Study study,
            @RequestParam(required = false) String transition) {
        logger.info("Updating study with technical ID: {}", id);
        
        try {
            EntityWithMetadata<Study> existingStudy = entityService.findById(createModelSpec(), id, Study.class);
            if (existingStudy == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Study> updatedStudy;
            if (transition != null && !transition.trim().isEmpty()) {
                updatedStudy = entityService.save(study, transition, Study.class);
            } else {
                updatedStudy = entityService.save(study, Study.class);
            }
            
            logger.info("Successfully updated study: {}", study.getStudyId());
            return ResponseEntity.ok(updatedStudy);
        } catch (Exception e) {
            logger.error("Error updating study: {}", study.getStudyId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search studies by criteria
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Study>>> searchStudies(@RequestBody StudySearchRequest searchRequest) {
        logger.info("Searching studies with criteria");
        
        try {
            List<QueryCondition> conditions = new ArrayList<>();

            // Add search conditions based on request
            if (searchRequest.getPhase() != null && !searchRequest.getPhase().trim().isEmpty()) {
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.phase")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getPhase()));
                conditions.add(condition);
            }

            if (searchRequest.getStudyType() != null && !searchRequest.getStudyType().trim().isEmpty()) {
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.studyType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStudyType()));
                conditions.add(condition);
            }

            if (searchRequest.getSponsorName() != null && !searchRequest.getSponsorName().trim().isEmpty()) {
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.sponsor.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSponsorName()));
                conditions.add(condition);
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Study>> results = entityService.search(createModelSpec(), groupCondition, Study.class);
            logger.info("Found {} studies matching criteria", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching studies", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all studies (with pagination support)
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Study>>> getAllStudies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        logger.info("Retrieving all studies - page: {}, size: {}", page, size);
        
        try {
            // For simplicity, using search with empty conditions to get all
            GroupCondition emptyCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>());

            List<EntityWithMetadata<Study>> results = entityService.search(createModelSpec(), emptyCondition, Study.class);
            logger.info("Retrieved {} studies", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving all studies", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete study by technical ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudy(@PathVariable UUID id) {
        logger.info("Deleting study with technical ID: {}", id);
        
        try {
            entityService.delete(createModelSpec(), id);
            logger.info("Successfully deleted study with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting study with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get study enrollment summary
     */
    @GetMapping("/{id}/enrollment")
    public ResponseEntity<StudyEnrollmentSummary> getStudyEnrollment(@PathVariable UUID id) {
        logger.info("Retrieving enrollment summary for study: {}", id);
        
        try {
            EntityWithMetadata<Study> study = entityService.findById(createModelSpec(), id, Study.class);
            if (study == null) {
                return ResponseEntity.notFound().build();
            }

            StudyEnrollmentSummary summary = new StudyEnrollmentSummary();
            summary.setStudyId(study.entity().getStudyId());
            summary.setPlannedEnrollment(study.entity().getPlannedEnrollment());
            summary.setCurrentEnrollment(study.entity().getCurrentEnrollment());
            summary.setActualEnrollment(study.entity().getActualEnrollment());
            summary.setState(study.metadata().getState());

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Error retrieving enrollment summary for study: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Creates model specification for Study entity
     */
    private ModelSpec createModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(Study.ENTITY_NAME);
        modelSpec.setVersion(Study.ENTITY_VERSION);
        return modelSpec;
    }

    /**
     * Search request DTO
     */
    @Getter
    @Setter
    public static class StudySearchRequest {
        private String phase;
        private String studyType;
        private String sponsorName;
    }

    /**
     * Enrollment summary DTO
     */
    @Getter
    @Setter
    public static class StudyEnrollmentSummary {
        private String studyId;
        private Integer plannedEnrollment;
        private Integer currentEnrollment;
        private Integer actualEnrollment;
        private String state;
    }
}
