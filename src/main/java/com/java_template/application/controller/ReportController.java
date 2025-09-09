package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.report_entity.version_1.ReportEntity;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ReportController - REST controller for Report entity operations
 * Base Path: /api/reports
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReportController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new report entity
     * POST /api/reports
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<ReportEntity>> createReport(@RequestBody ReportEntity entity) {
        try {
            // Set generation date
            entity.setGenerationDate(LocalDateTime.now());

            // Set default report type if not provided
            if (entity.getReportType() == null || entity.getReportType().trim().isEmpty()) {
                entity.setReportType("WEEKLY_PERFORMANCE");
            }

            // Set default file format if not provided
            if (entity.getFileFormat() == null || entity.getFileFormat().trim().isEmpty()) {
                entity.setFileFormat("PDF");
            }

            // Generate report ID if not provided
            if (entity.getReportId() == null || entity.getReportId().trim().isEmpty()) {
                entity.setReportId("report-" + System.currentTimeMillis());
            }

            EntityWithMetadata<ReportEntity> response = entityService.create(entity);
            logger.info("Report created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get report by technical UUID
     * GET /api/reports/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<ReportEntity>> getReportById(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ReportEntity.ENTITY_NAME).withVersion(ReportEntity.ENTITY_VERSION);
            EntityWithMetadata<ReportEntity> response = entityService.getById(uuid, modelSpec, ReportEntity.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Report by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update report entity with optional state transition
     * PUT /api/reports/{uuid}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<ReportEntity>> updateReport(
            @PathVariable UUID uuid,
            @RequestBody ReportEntity entity,
            @RequestParam(required = false) String transitionName) {
        try {
            EntityWithMetadata<ReportEntity> response = entityService.update(uuid, entity, transitionName);
            logger.info("Report updated with ID: {}", uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete report entity
     * DELETE /api/reports/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID uuid) {
        try {
            entityService.deleteById(uuid);
            logger.info("Report deleted with ID: {}", uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all reports with optional filtering
     * GET /api/reports?reportType=WEEKLY_PERFORMANCE&startDate=2024-01-01
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<ReportEntity>>> getAllReports(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ReportEntity.ENTITY_NAME).withVersion(ReportEntity.ENTITY_VERSION);
            
            // Build search conditions
            List<QueryCondition> conditions = new ArrayList<>();
            
            if (reportType != null && !reportType.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.reportType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(reportType)));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<ReportEntity>> entities = entityService.search(modelSpec, condition, ReportEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all Reports", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Download report file
     * GET /api/reports/{uuid}/download
     */
    @GetMapping("/{uuid}/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ReportEntity.ENTITY_NAME).withVersion(ReportEntity.ENTITY_VERSION);
            EntityWithMetadata<ReportEntity> reportWithMetadata = entityService.getById(uuid, modelSpec, ReportEntity.class);
            ReportEntity report = reportWithMetadata.entity();

            if (report.getFilePath() == null || report.getFilePath().trim().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // In real implementation, this would read the actual file from the file system
            // For now, we'll return a mock PDF content
            String mockContent = "Mock PDF content for report: " + report.getReportId();
            ByteArrayResource resource = new ByteArrayResource(mockContent.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + report.getReportId() + ".pdf");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error downloading Report: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Search reports by criteria
     * POST /api/reports/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<ReportEntity>>> searchReports(@RequestBody ReportSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ReportEntity.ENTITY_NAME).withVersion(ReportEntity.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getReportType() != null && !searchRequest.getReportType().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.reportType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getReportType())));
            }

            if (searchRequest.getFileFormat() != null && !searchRequest.getFileFormat().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.fileFormat")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getFileFormat())));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<ReportEntity>> entities = entityService.search(modelSpec, condition, ReportEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching Reports", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for report search requests
     */
    @Getter
    @Setter
    public static class ReportSearchRequest {
        private String reportType;
        private String fileFormat;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }
}
