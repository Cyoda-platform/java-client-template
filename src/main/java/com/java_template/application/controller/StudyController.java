package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.study.version_1.Study;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for study management
 * Handles operational study lifecycle
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
            study.setCreatedAt(LocalDateTime.now());
            study.setUpdatedAt(LocalDateTime.now());

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
     * Get study by business ID
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
     * Activate study
     * POST /ui/studies/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<EntityWithMetadata<Study>> activateStudy(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Study.ENTITY_NAME).withVersion(Study.ENTITY_VERSION);
            EntityWithMetadata<Study> current = entityService.getById(id, modelSpec, Study.class);
            
            current.entity().setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Study> response = entityService.update(id, current.entity(), "activate_study");
            
            logger.info("Study {} activated", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating study", e);
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
     * Delete study
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
}
