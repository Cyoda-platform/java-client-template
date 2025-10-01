package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subject.version_1.Subject;
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
 * REST controller for subject management
 * Handles study participant lifecycle
 */
@RestController
@RequestMapping("/ui/subjects")
@CrossOrigin(origins = "*")
public class SubjectController {

    private static final Logger logger = LoggerFactory.getLogger(SubjectController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubjectController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new subject
     * POST /ui/subjects
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Subject>> createSubject(@RequestBody Subject subject) {
        try {
            subject.setCreatedAt(LocalDateTime.now());
            subject.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Subject> response = entityService.create(subject);
            logger.info("Subject created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating subject", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get subject by technical UUID
     * GET /ui/subjects/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subject>> getSubjectById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subject.ENTITY_NAME).withVersion(Subject.ENTITY_VERSION);
            EntityWithMetadata<Subject> response = entityService.getById(id, modelSpec, Subject.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting subject by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get subject by business ID
     * GET /ui/subjects/business/{subjectId}
     */
    @GetMapping("/business/{subjectId}")
    public ResponseEntity<EntityWithMetadata<Subject>> getSubjectByBusinessId(@PathVariable String subjectId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subject.ENTITY_NAME).withVersion(Subject.ENTITY_VERSION);
            EntityWithMetadata<Subject> response = entityService.findByBusinessId(
                    modelSpec, subjectId, "subjectId", Subject.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting subject by business ID: {}", subjectId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update subject with optional workflow transition
     * PUT /ui/subjects/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Subject>> updateSubject(
            @PathVariable UUID id,
            @RequestBody Subject subject,
            @RequestParam(required = false) String transition) {
        try {
            subject.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Subject> response = entityService.update(id, subject, transition);
            logger.info("Subject updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating subject", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Enroll subject
     * POST /ui/subjects/{id}/enroll
     */
    @PostMapping("/{id}/enroll")
    public ResponseEntity<EntityWithMetadata<Subject>> enrollSubject(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subject.ENTITY_NAME).withVersion(Subject.ENTITY_VERSION);
            EntityWithMetadata<Subject> current = entityService.getById(id, modelSpec, Subject.class);
            
            current.entity().setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Subject> response = entityService.update(id, current.entity(), "enroll_subject");
            
            logger.info("Subject {} enrolled", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error enrolling subject", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get subjects by study
     * GET /ui/subjects/study/{studyId}
     */
    @GetMapping("/study/{studyId}")
    public ResponseEntity<List<EntityWithMetadata<Subject>>> getSubjectsByStudy(@PathVariable String studyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subject.ENTITY_NAME).withVersion(Subject.ENTITY_VERSION);

            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.studyId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(studyId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Subject>> subjects = entityService.search(modelSpec, groupCondition, Subject.class);
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            logger.error("Error getting subjects by study: {}", studyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search subjects by criteria
     * POST /ui/subjects/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Subject>>> searchSubjects(
            @RequestBody SubjectSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Subject.ENTITY_NAME).withVersion(Subject.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getStudyId() != null && !searchRequest.getStudyId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.studyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStudyId())));
            }

            if (searchRequest.getStatus() != null && !searchRequest.getStatus().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStatus())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Subject>> subjects = entityService.search(modelSpec, condition, Subject.class);
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            logger.error("Error searching subjects", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for subject search requests
     */
    @Getter
    @Setter
    public static class SubjectSearchRequest {
        private String studyId;
        private String status;
    }
}
