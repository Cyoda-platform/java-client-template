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
 * SubmissionController - REST controller for Submission entity operations
 * Handles submission creation, review workflow, and management
 */
@RestController
@RequestMapping("/ui/submission")
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
     * POST /ui/submission
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Submission>> createSubmission(@RequestBody Submission submission) {
        try {
            // Set submission timestamp
            submission.setSubmissionDate(LocalDateTime.now());
            
            // Set default values if not provided
            if (submission.getPriority() == null || submission.getPriority().trim().isEmpty()) {
                submission.setPriority("MEDIUM"); // Default priority
            }
            
            // Set default target decision date if not provided (30 days from now)
            if (submission.getTargetDecisionDate() == null) {
                submission.setTargetDecisionDate(LocalDateTime.now().plusDays(30));
            }

            EntityWithMetadata<Submission> response = entityService.create(submission);
            logger.info("Submission created with ID: {} and title: {}", response.metadata().getId(), submission.getTitle());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get submission by technical UUID
     * GET /ui/submission/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Submission>> getSubmissionById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> response = entityService.getById(id, modelSpec, Submission.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Submission by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update submission with optional workflow transition
     * PUT /ui/submission/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Submission>> updateSubmission(
            @PathVariable UUID id,
            @RequestBody Submission submission,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Submission> response = entityService.update(id, submission, transition);
            logger.info("Submission updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Submit for review
     * PUT /ui/submission/{id}/submit
     */
    @PutMapping("/{id}/submit")
    public ResponseEntity<EntityWithMetadata<Submission>> submitForReview(@PathVariable UUID id) {
        try {
            // Get current submission
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> currentSubmission = entityService.getById(id, modelSpec, Submission.class);
            
            Submission submission = currentSubmission.entity();
            
            EntityWithMetadata<Submission> response = entityService.update(id, submission, "submit_for_review");
            logger.info("Submission submitted for review with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting Submission for review", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Assign reviewer
     * PUT /ui/submission/{id}/assign-reviewer
     */
    @PutMapping("/{id}/assign-reviewer")
    public ResponseEntity<EntityWithMetadata<Submission>> assignReviewer(
            @PathVariable UUID id, 
            @RequestBody ReviewerAssignmentRequest request) {
        try {
            // Get current submission
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> currentSubmission = entityService.getById(id, modelSpec, Submission.class);
            
            Submission submission = currentSubmission.entity();
            submission.setReviewerEmail(request.getReviewerEmail());
            
            EntityWithMetadata<Submission> response = entityService.update(id, submission, "assign_reviewer");
            logger.info("Reviewer {} assigned to submission with ID: {}", request.getReviewerEmail(), id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error assigning reviewer to Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Approve submission
     * PUT /ui/submission/{id}/approve
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<EntityWithMetadata<Submission>> approveSubmission(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request) {
        try {
            // Get current submission
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> currentSubmission = entityService.getById(id, modelSpec, Submission.class);
            
            Submission submission = currentSubmission.entity();
            if (request.getDecisionReason() != null) {
                submission.setDecisionReason(request.getDecisionReason());
            }
            
            EntityWithMetadata<Submission> response = entityService.update(id, submission, "approve_submission");
            logger.info("Submission approved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error approving Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reject submission
     * PUT /ui/submission/{id}/reject
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<EntityWithMetadata<Submission>> rejectSubmission(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request) {
        try {
            // Get current submission
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> currentSubmission = entityService.getById(id, modelSpec, Submission.class);
            
            Submission submission = currentSubmission.entity();
            submission.setDecisionReason(request.getDecisionReason());
            
            EntityWithMetadata<Submission> response = entityService.update(id, submission, "reject_submission");
            logger.info("Submission rejected with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error rejecting Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request revision
     * PUT /ui/submission/{id}/request-revision
     */
    @PutMapping("/{id}/request-revision")
    public ResponseEntity<EntityWithMetadata<Submission>> requestRevision(@PathVariable UUID id) {
        try {
            // Get current submission
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> currentSubmission = entityService.getById(id, modelSpec, Submission.class);
            
            Submission submission = currentSubmission.entity();
            
            EntityWithMetadata<Submission> response = entityService.update(id, submission, "request_revision");
            logger.info("Revision requested for submission with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error requesting revision for Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Withdraw submission
     * PUT /ui/submission/{id}/withdraw
     */
    @PutMapping("/{id}/withdraw")
    public ResponseEntity<EntityWithMetadata<Submission>> withdrawSubmission(@PathVariable UUID id) {
        try {
            // Get current submission
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            EntityWithMetadata<Submission> currentSubmission = entityService.getById(id, modelSpec, Submission.class);
            
            Submission submission = currentSubmission.entity();
            
            EntityWithMetadata<Submission> response = entityService.update(id, submission, "withdraw_submission");
            logger.info("Submission withdrawn with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error withdrawing Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete submission by technical UUID
     * DELETE /ui/submission/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubmission(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Submission deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Submission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all submissions
     * GET /ui/submission
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Submission>>> getAllSubmissions() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Submission.ENTITY_NAME).withVersion(Submission.ENTITY_VERSION);
            List<EntityWithMetadata<Submission>> submissions = entityService.findAll(modelSpec, Submission.class);
            return ResponseEntity.ok(submissions);
        } catch (Exception e) {
            logger.error("Error getting all Submissions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for reviewer assignment requests
     */
    @Getter
    @Setter
    public static class ReviewerAssignmentRequest {
        private String reviewerEmail;
    }

    /**
     * DTO for decision requests (approve/reject)
     */
    @Getter
    @Setter
    public static class DecisionRequest {
        private String decisionReason;
    }
}
