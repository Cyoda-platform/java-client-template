package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.submission.version_1.Submission;
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
 * REST controller for submission management
 * Handles clinical trial and research study submissions
 */
@RestController
@RequestMapping("/ui/submissions")
@CrossOrigin(origins = "*")
public class SubmissionController {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubmissionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new submission
     * POST /ui/submissions
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Submission>> createSubmission(@RequestBody Submission submission) {
        try {
            submission.setCreatedAt(LocalDateTime.now());
            submission.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Submission> response = entityService.create(submission);
            logger.info("Submission created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get submission by technical UUID
     * GET /ui/submissions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Submission>> getSubmissionById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> response = entityService.getById(id, modelSpec, Submission.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting submission by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get submission by business ID
     * GET /ui/submissions/business/{submissionId}
     */
    @GetMapping("/business/{submissionId}")
    public ResponseEntity<EntityWithMetadata<Submission>> getSubmissionByBusinessId(@PathVariable String submissionId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> response = entityService.findByBusinessId(
                    modelSpec, submissionId, "submissionId", Submission.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting submission by business ID: {}", submissionId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update submission with optional workflow transition
     * PUT /ui/submissions/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Submission>> updateSubmission(
            @PathVariable UUID id,
            @RequestBody Submission submission,
            @RequestParam(required = false) String transition) {
        try {
            submission.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Submission> response = entityService.update(id, submission, transition);
            logger.info("Submission updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Submit for review (workflow transition)
     * POST /ui/submissions/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<EntityWithMetadata<Submission>> submitForReview(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> current = entityService.getById(id, modelSpec, Submission.class);
            
            current.entity().setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Submission> response = entityService.update(id, current.entity(), "submit_for_review");
            
            logger.info("Submission {} submitted for review", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting submission for review", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all submissions
     * GET /ui/submissions
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Submission>>> getAllSubmissions() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            List<EntityWithMetadata<Submission>> submissions = entityService.findAll(modelSpec, Submission.class);
            return ResponseEntity.ok(submissions);
        } catch (Exception e) {
            logger.error("Error getting all submissions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search submissions by criteria
     * POST /ui/submissions/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Submission>>> searchSubmissions(
            @RequestBody SubmissionSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getStudyType() != null && !searchRequest.getStudyType().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.studyType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStudyType())));
            }

            if (searchRequest.getSponsorName() != null && !searchRequest.getSponsorName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.sponsorName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSponsorName())));
            }

            if (searchRequest.getTherapeuticArea() != null && !searchRequest.getTherapeuticArea().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.therapeuticArea")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getTherapeuticArea())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Submission>> submissions = entityService.search(modelSpec, condition, Submission.class);
            return ResponseEntity.ok(submissions);
        } catch (Exception e) {
            logger.error("Error searching submissions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for submission search requests
     */
    @Getter
    @Setter
    public static class SubmissionSearchRequest {
        private String studyType;
        private String sponsorName;
        private String therapeuticArea;
    }
}
