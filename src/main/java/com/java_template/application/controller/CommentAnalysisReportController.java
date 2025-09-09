package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.PageResponse;
import com.java_template.application.entity.commentanalysisreport.version_1.CommentAnalysisReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CommentAnalysisReportController
 * 
 * REST controller for managing CommentAnalysisReport entities.
 * Provides read operations for analysis reports.
 */
@RestController
@RequestMapping("/api/comment-analysis-reports")
@CrossOrigin(origins = "*")
public class CommentAnalysisReportController {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisReportController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisReportController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get report by technical UUID
     * GET /api/comment-analysis-reports/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysisReport>> getReportById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisReport.ENTITY_NAME).withVersion(CommentAnalysisReport.ENTITY_VERSION);
            EntityWithMetadata<CommentAnalysisReport> response = entityService.getById(id, modelSpec, CommentAnalysisReport.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting CommentAnalysisReport by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get report by job UUID
     * GET /api/comment-analysis-reports/by-job/{jobUuid}
     */
    @GetMapping("/by-job/{jobUuid}")
    public ResponseEntity<EntityWithMetadata<CommentAnalysisReport>> getReportByJobId(@PathVariable String jobUuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisReport.ENTITY_NAME).withVersion(CommentAnalysisReport.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.jobId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(jobUuid));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<CommentAnalysisReport>> reports = entityService.search(modelSpec, condition, CommentAnalysisReport.class);
            
            if (reports.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(reports.get(0));
        } catch (Exception e) {
            logger.error("Error getting CommentAnalysisReport by job ID: {}", jobUuid, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all reports with filtering options
     * GET /api/comment-analysis-reports
     */
    @GetMapping
    public ResponseEntity<PageResponse<EntityWithMetadata<CommentAnalysisReport>>> getAllReports(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisReport.ENTITY_NAME).withVersion(CommentAnalysisReport.ENTITY_VERSION);
            
            List<EntityWithMetadata<CommentAnalysisReport>> reports;
            
            if (jobId != null || postId != null) {
                // Build search condition
                List<SimpleCondition> conditions = new ArrayList<>();
                
                if (jobId != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.jobId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(jobId)));
                }
                
                if (postId != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.postId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(postId)));
                }
                
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(new ArrayList<>(conditions));
                
                reports = entityService.search(modelSpec, condition, CommentAnalysisReport.class);
            } else {
                reports = entityService.findAll(modelSpec, CommentAnalysisReport.class);
            }
            
            // Filter by state if provided (since state is in metadata, not entity)
            if (state != null) {
                reports = reports.stream()
                        .filter(report -> state.equals(report.metadata().getState()))
                        .toList();
            }
            
            // Manual pagination
            int start = page * size;
            int end = Math.min(start + size, reports.size());

            List<EntityWithMetadata<CommentAnalysisReport>> pageContent = reports.subList(start, end);
            PageResponse<EntityWithMetadata<CommentAnalysisReport>> pageResult = new PageResponse<>(pageContent, page, size, reports.size());

            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            logger.error("Error getting CommentAnalysisReports", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
