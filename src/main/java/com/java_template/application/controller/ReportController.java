package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.report.version_1.Report;
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
import java.util.List;
import java.util.UUID;

/**
 * ReportController - REST controller for Report entity operations
 * Handles report generation, completion, and management
 */
@RestController
@RequestMapping("/ui/report")
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
     * Generate a new report
     * POST /ui/report
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Report>> generateReport(@RequestBody Report report) {
        try {
            // Set generation timestamp
            report.setGenerationDate(LocalDateTime.now());
            
            // Set default values if not provided
            if (report.getFormat() == null || report.getFormat().trim().isEmpty()) {
                report.setFormat("PDF"); // Default format
            }
            
            if (report.getParameters() == null || report.getParameters().trim().isEmpty()) {
                report.setParameters("{}"); // Empty JSON object
            }

            EntityWithMetadata<Report> response = entityService.create(report);
            logger.info("Report generation started with ID: {} and name: {}", response.metadata().getId(), report.getReportName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error generating Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get report by technical UUID
     * GET /ui/report/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Report>> getReportById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> response = entityService.getById(id, modelSpec, Report.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Report by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update report with optional workflow transition
     * PUT /ui/report/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Report>> updateReport(
            @PathVariable UUID id,
            @RequestBody Report report,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Report> response = entityService.update(id, report, transition);
            logger.info("Report updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Complete report generation
     * PUT /ui/report/{id}/complete
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<EntityWithMetadata<Report>> completeReport(@PathVariable UUID id) {
        try {
            // Get current report
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> currentReport = entityService.getById(id, modelSpec, Report.class);
            
            Report report = currentReport.entity();
            
            EntityWithMetadata<Report> response = entityService.update(id, report, "complete_generation");
            logger.info("Report generation completed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error completing Report generation", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark report generation as failed
     * PUT /ui/report/{id}/fail
     */
    @PutMapping("/{id}/fail")
    public ResponseEntity<EntityWithMetadata<Report>> failReport(@PathVariable UUID id) {
        try {
            // Get current report
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> currentReport = entityService.getById(id, modelSpec, Report.class);
            
            Report report = currentReport.entity();
            
            EntityWithMetadata<Report> response = entityService.update(id, report, "fail_generation");
            logger.info("Report generation failed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error failing Report generation", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry report generation
     * PUT /ui/report/{id}/retry
     */
    @PutMapping("/{id}/retry")
    public ResponseEntity<EntityWithMetadata<Report>> retryReport(@PathVariable UUID id) {
        try {
            // Get current report
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> currentReport = entityService.getById(id, modelSpec, Report.class);
            
            Report report = currentReport.entity();
            
            EntityWithMetadata<Report> response = entityService.update(id, report, "retry_generation");
            logger.info("Report generation retried with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrying Report generation", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Archive report
     * PUT /ui/report/{id}/archive
     */
    @PutMapping("/{id}/archive")
    public ResponseEntity<EntityWithMetadata<Report>> archiveReport(@PathVariable UUID id) {
        try {
            // Get current report
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> currentReport = entityService.getById(id, modelSpec, Report.class);
            
            Report report = currentReport.entity();
            
            EntityWithMetadata<Report> response = entityService.update(id, report, "archive_report");
            logger.info("Report archived with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error archiving Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete report by technical UUID
     * DELETE /ui/report/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Report deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all reports
     * GET /ui/report
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Report>>> getAllReports() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);
            List<EntityWithMetadata<Report>> reports = entityService.findAll(modelSpec, Report.class);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error getting all Reports", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get reports by generator
     * GET /ui/report/generator/{generatorEmail}
     */
    @GetMapping("/generator/{generatorEmail}")
    public ResponseEntity<List<EntityWithMetadata<Report>>> getReportsByGenerator(@PathVariable String generatorEmail) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.generatedBy")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(generatorEmail));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Report>> reports = entityService.search(modelSpec, condition, Report.class);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error getting Reports by generator: {}", generatorEmail, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search reports by type
     * GET /ui/report/search/type?reportType=TYPE
     */
    @GetMapping("/search/type")
    public ResponseEntity<List<EntityWithMetadata<Report>>> searchReportsByType(@RequestParam String reportType) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.reportType")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(reportType));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Report>> reports = entityService.search(modelSpec, condition, Report.class);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error searching Reports by type: {}", reportType, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search reports by format
     * GET /ui/report/search/format?format=FORMAT
     */
    @GetMapping("/search/format")
    public ResponseEntity<List<EntityWithMetadata<Report>>> searchReportsByFormat(@RequestParam String format) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Report.ENTITY_NAME).withVersion(Report.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.format")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(format));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Report>> reports = entityService.search(modelSpec, condition, Report.class);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error searching Reports by format: {}", format, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
