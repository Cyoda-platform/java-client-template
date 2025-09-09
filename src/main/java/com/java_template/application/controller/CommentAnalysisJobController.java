package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.PageResponse;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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
 * CommentAnalysisJobController
 * 
 * REST controller for managing CommentAnalysisJob entities.
 * Provides CRUD operations and workflow transitions.
 */
@RestController
@RequestMapping("/api/comment-analysis-jobs")
@CrossOrigin(origins = "*")
public class CommentAnalysisJobController {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisJobController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new comment analysis job
     * POST /api/comment-analysis-jobs
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<CommentAnalysisJob>> createJob(@RequestBody CommentAnalysisJob job) {
        try {
            // Set creation timestamp
            job.setRequestedAt(LocalDateTime.now());

            // Create entity (will auto-transition to PENDING state)
            EntityWithMetadata<CommentAnalysisJob> response = entityService.create(job);
            logger.info("CommentAnalysisJob created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating CommentAnalysisJob", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get job by technical UUID
     * GET /api/comment-analysis-jobs/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysisJob>> getJobById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisJob.ENTITY_NAME).withVersion(CommentAnalysisJob.ENTITY_VERSION);
            EntityWithMetadata<CommentAnalysisJob> response = entityService.getById(id, modelSpec, CommentAnalysisJob.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting CommentAnalysisJob by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all jobs with optional filtering
     * GET /api/comment-analysis-jobs
     */
    @GetMapping
    public ResponseEntity<PageResponse<EntityWithMetadata<CommentAnalysisJob>>> getAllJobs(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisJob.ENTITY_NAME).withVersion(CommentAnalysisJob.ENTITY_VERSION);
            
            List<EntityWithMetadata<CommentAnalysisJob>> jobs;
            
            if (state != null || postId != null) {
                // Build search condition
                List<SimpleCondition> conditions = new ArrayList<>();
                
                if (postId != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.postId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(postId)));
                }
                
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(new ArrayList<>(conditions));
                
                jobs = entityService.search(modelSpec, condition, CommentAnalysisJob.class);
                
                // Filter by state if provided (since state is in metadata, not entity)
                if (state != null) {
                    jobs = jobs.stream()
                            .filter(job -> state.equals(job.metadata().getState()))
                            .toList();
                }
            } else {
                jobs = entityService.findAll(modelSpec, CommentAnalysisJob.class);
            }
            
            // Manual pagination
            int start = page * size;
            int end = Math.min(start + size, jobs.size());

            List<EntityWithMetadata<CommentAnalysisJob>> pageContent = jobs.subList(start, end);
            PageResponse<EntityWithMetadata<CommentAnalysisJob>> pageResult = new PageResponse<>(pageContent, page, size, jobs.size());

            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            logger.error("Error getting CommentAnalysisJobs", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update job with optional workflow transition
     * PUT /api/comment-analysis-jobs/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysisJob>> updateJob(
            @PathVariable UUID id,
            @RequestBody UpdateJobRequest request) {
        try {
            // Get existing job first
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisJob.ENTITY_NAME).withVersion(CommentAnalysisJob.ENTITY_VERSION);
            EntityWithMetadata<CommentAnalysisJob> existing = entityService.getById(id, modelSpec, CommentAnalysisJob.class);
            
            CommentAnalysisJob job = existing.entity();
            
            // Update fields if provided
            if (request.getRecipientEmail() != null) {
                job.setRecipientEmail(request.getRecipientEmail());
            }

            // Update entity with optional transition
            EntityWithMetadata<CommentAnalysisJob> response = entityService.update(id, job, request.getTransitionName());
            logger.info("CommentAnalysisJob updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating CommentAnalysisJob", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete job by technical UUID
     * DELETE /api/comment-analysis-jobs/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("CommentAnalysisJob deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting CommentAnalysisJob", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for update requests
     */
    @Getter
    @Setter
    public static class UpdateJobRequest {
        private String recipientEmail;
        private String transitionName;
    }
}
