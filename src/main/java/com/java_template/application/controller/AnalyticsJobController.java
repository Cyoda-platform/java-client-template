package com.java_template.application.controller;

import com.java_template.application.entity.analyticsjob.version_1.AnalyticsJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics-jobs")
public class AnalyticsJobController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsJobController.class);
    private final EntityService entityService;

    public AnalyticsJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<AnalyticsJob>>> getAllAnalyticsJobs() {
        try {
            logger.info("Retrieving all analytics jobs");
            List<EntityResponse<AnalyticsJob>> jobs = entityService.findAll(AnalyticsJob.class);
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            logger.error("Failed to retrieve analytics jobs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<AnalyticsJob>> getAnalyticsJobById(@PathVariable UUID id) {
        try {
            logger.info("Retrieving analytics job by ID: {}", id);
            EntityResponse<AnalyticsJob> job = entityService.getById(id, AnalyticsJob.class);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            logger.error("Failed to retrieve analytics job {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/business/{jobId}")
    public ResponseEntity<EntityResponse<AnalyticsJob>> getAnalyticsJobByBusinessId(@PathVariable String jobId) {
        try {
            logger.info("Retrieving analytics job by business ID: {}", jobId);
            EntityResponse<AnalyticsJob> job = entityService.findByBusinessId(AnalyticsJob.class, jobId, "jobId");
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            logger.error("Failed to retrieve analytics job by business ID {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<EntityResponse<AnalyticsJob>> createAnalyticsJob(@RequestBody AnalyticsJob job) {
        try {
            logger.info("Creating new analytics job of type: {}", job.getJobType());
            EntityResponse<AnalyticsJob> savedJob = entityService.save(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedJob);
        } catch (Exception e) {
            logger.error("Failed to create analytics job: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<AnalyticsJob>> updateAnalyticsJob(
            @PathVariable UUID id,
            @RequestBody AnalyticsJob job,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating analytics job {} with transition: {}", id, transition);
            EntityResponse<AnalyticsJob> updatedJob = entityService.update(id, job, transition);
            return ResponseEntity.ok(updatedJob);
        } catch (Exception e) {
            logger.error("Failed to update analytics job {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAnalyticsJob(@PathVariable UUID id) {
        try {
            logger.info("Deleting analytics job: {}", id);
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete analytics job {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Invalid request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred");
    }
}
