package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment_analysis.version_1.CommentAnalysis;
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

import java.util.List;
import java.util.UUID;

/**
 * CommentAnalysisController - Manage comment analysis for posts
 * 
 * Base Path: /api/analysis
 * Purpose: Manage comment analysis for posts
 */
@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class CommentAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Start analysis for a specific post
     * POST /api/analysis/start/{postId}
     */
    @PostMapping("/start/{postId}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysis>> startAnalysis(
            @PathVariable Integer postId, 
            @RequestBody(required = false) StartAnalysisRequest request) {
        try {
            logger.info("Starting analysis for postId: {}", postId);

            // Check if analysis already exists for this postId
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysis.ENTITY_NAME).withVersion(CommentAnalysis.ENTITY_VERSION);

            SimpleCondition postIdCondition = new SimpleCondition()
                    .withJsonPath("$.postId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(postId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(postIdCondition));

            List<EntityWithMetadata<CommentAnalysis>> existingAnalyses = 
                    entityService.search(modelSpec, condition, CommentAnalysis.class);

            if (!existingAnalyses.isEmpty()) {
                logger.info("Analysis already exists for postId: {}", postId);
                return ResponseEntity.ok(existingAnalyses.get(0));
            }

            // Create new analysis
            CommentAnalysis analysis = new CommentAnalysis();
            analysis.setAnalysisId(UUID.randomUUID().toString());
            analysis.setPostId(postId);
            analysis.setTotalComments(0);
            analysis.setEmailSent(false);

            // Create analysis entity (triggers workflow: none → collecting)
            EntityWithMetadata<CommentAnalysis> response = entityService.create(analysis);
            logger.info("Analysis started for postId: {} with ID: {}", postId, response.metadata().getId());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting analysis for postId: {}", postId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Begin processing collected comments
     * PUT /api/analysis/{id}/process
     */
    @PutMapping("/{id}/process")
    public ResponseEntity<EntityWithMetadata<CommentAnalysis>> processAnalysis(
            @PathVariable UUID id,
            @RequestParam(required = false) String transition) {
        try {
            // Get current analysis
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysis.ENTITY_NAME).withVersion(CommentAnalysis.ENTITY_VERSION);
            EntityWithMetadata<CommentAnalysis> currentAnalysis = entityService.getById(id, modelSpec, CommentAnalysis.class);

            // Use provided transition or default to begin_processing
            String transitionName = (transition != null) ? transition : "begin_processing";

            // Update with transition to trigger processing
            EntityWithMetadata<CommentAnalysis> response = entityService.update(id, currentAnalysis.entity(), transitionName);
            logger.info("Analysis processing started for ID: {}", id);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing analysis for ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate email report
     * PUT /api/analysis/{id}/report
     */
    @PutMapping("/{id}/report")
    public ResponseEntity<EntityWithMetadata<CommentAnalysis>> generateReport(
            @PathVariable UUID id,
            @RequestParam(required = false) String transition) {
        try {
            // Get current analysis
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysis.ENTITY_NAME).withVersion(CommentAnalysis.ENTITY_VERSION);
            EntityWithMetadata<CommentAnalysis> currentAnalysis = entityService.getById(id, modelSpec, CommentAnalysis.class);

            // Use provided transition or default to generate_report
            String transitionName = (transition != null) ? transition : "generate_report";

            // Update with transition to trigger report generation
            EntityWithMetadata<CommentAnalysis> response = entityService.update(id, currentAnalysis.entity(), transitionName);
            logger.info("Report generation started for analysis ID: {}", id);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error generating report for analysis ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get analysis by technical UUID
     * GET /api/analysis/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysis>> getAnalysisById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysis.ENTITY_NAME).withVersion(CommentAnalysis.ENTITY_VERSION);
            EntityWithMetadata<CommentAnalysis> response = entityService.getById(id, modelSpec, CommentAnalysis.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting CommentAnalysis by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get analysis for specific post
     * GET /api/analysis/post/{postId}
     */
    @GetMapping("/post/{postId}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysis>> getAnalysisByPostId(@PathVariable Integer postId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysis.ENTITY_NAME).withVersion(CommentAnalysis.ENTITY_VERSION);

            SimpleCondition postIdCondition = new SimpleCondition()
                    .withJsonPath("$.postId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(postId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(postIdCondition));

            List<EntityWithMetadata<CommentAnalysis>> analyses = entityService.search(modelSpec, condition, CommentAnalysis.class);
            
            if (analyses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(analyses.get(0));
        } catch (Exception e) {
            logger.error("Error getting CommentAnalysis by postId: {}", postId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update analysis with optional transition
     * PUT /api/analysis/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysis>> updateAnalysis(
            @PathVariable UUID id,
            @RequestBody CommentAnalysis analysis,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<CommentAnalysis> response = entityService.update(id, analysis, transition);
            logger.info("CommentAnalysis updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating CommentAnalysis", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class StartAnalysisRequest {
        private String recipientEmail;
    }
}
