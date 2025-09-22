package com.java_template.application.controller;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ReportController
 * 
 * REST API for managing analysis reports and email distribution.
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private final EntityService entityService;

    public ReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new report
     * POST /api/reports
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Report>> createReport(@RequestBody Report report) {
        try {
            // Generate unique reportId if not provided
            if (report.getReportId() == null || report.getReportId().trim().isEmpty()) {
                report.setReportId("rpt-" + UUID.randomUUID().toString().substring(0, 8));
            }

            // Set default report format if not provided
            if (report.getReportFormat() == null || report.getReportFormat().trim().isEmpty()) {
                report.setReportFormat("HTML");
            }

            // Validate required fields
            if (!report.isValid()) {
                logger.warn("Invalid Report provided: {}", report);
                return ResponseEntity.badRequest().build();
            }

            // Create the Report entity (creates in initial_state, then auto-transitions to created)
            EntityWithMetadata<Report> response = entityService.create(report);
            logger.info("Report created with ID: {} and business ID: {}", 
                       response.metadata().getId(), report.getReportId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Report", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate report content
     * PUT /api/reports/{id}/generate
     */
    @PutMapping("/{id}/generate")
    public ResponseEntity<EntityWithMetadata<Report>> generateReport(@PathVariable UUID id) {
        try {
            // Get current Report
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Report.ENTITY_NAME)
                    .withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> current = entityService.getById(id, modelSpec, Report.class);
            
            // Trigger start_generation transition
            EntityWithMetadata<Report> response = entityService.update(id, current.entity(), "start_generation");
            logger.info("Report generation triggered for Report ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering report generation for Report ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Send report to subscribers
     * PUT /api/reports/{id}/send
     */
    @PutMapping("/{id}/send")
    public ResponseEntity<EntityWithMetadata<Report>> sendReport(@PathVariable UUID id) {
        try {
            // Get current Report
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Report.ENTITY_NAME)
                    .withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> current = entityService.getById(id, modelSpec, Report.class);
            
            // Trigger send_to_subscribers transition
            EntityWithMetadata<Report> response = entityService.update(id, current.entity(), "send_to_subscribers");
            logger.info("Report sending triggered for Report ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering report sending for Report ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get report by technical UUID
     * GET /api/reports/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Report>> getReportById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Report.ENTITY_NAME)
                    .withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> response = entityService.getById(id, modelSpec, Report.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Report by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get report by business identifier
     * GET /api/reports/business/{reportId}
     */
    @GetMapping("/business/{reportId}")
    public ResponseEntity<EntityWithMetadata<Report>> getReportByBusinessId(@PathVariable String reportId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Report.ENTITY_NAME)
                    .withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> response = entityService.findByBusinessId(
                    modelSpec, reportId, "reportId", Report.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Report by business ID: {}", reportId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry report generation for failed reports
     * PUT /api/reports/{id}/retry-generation
     */
    @PutMapping("/{id}/retry-generation")
    public ResponseEntity<EntityWithMetadata<Report>> retryGeneration(@PathVariable UUID id) {
        try {
            // Get current Report
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Report.ENTITY_NAME)
                    .withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> current = entityService.getById(id, modelSpec, Report.class);
            
            // Trigger retry_generation transition (only valid from failed state)
            EntityWithMetadata<Report> response = entityService.update(id, current.entity(), "retry_generation");
            logger.info("Retry generation triggered for Report ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering retry generation for Report ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry report sending for failed reports
     * PUT /api/reports/{id}/retry-send
     */
    @PutMapping("/{id}/retry-send")
    public ResponseEntity<EntityWithMetadata<Report>> retrySend(@PathVariable UUID id) {
        try {
            // Get current Report
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Report.ENTITY_NAME)
                    .withVersion(Report.ENTITY_VERSION);
            EntityWithMetadata<Report> current = entityService.getById(id, modelSpec, Report.class);
            
            // Trigger retry_send transition (only valid from failed state)
            EntityWithMetadata<Report> response = entityService.update(id, current.entity(), "retry_send");
            logger.info("Retry send triggered for Report ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering retry send for Report ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
