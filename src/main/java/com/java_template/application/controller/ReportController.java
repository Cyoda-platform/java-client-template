package com.java_template.application.controller;

import com.java_template.application.entity.report.version_1.Report;
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
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private final EntityService entityService;

    public ReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Report>>> getAllReports() {
        try {
            logger.info("Retrieving all reports");
            List<EntityResponse<Report>> reports = entityService.findAll(Report.class);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Failed to retrieve reports: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Report>> getReportById(@PathVariable UUID id) {
        try {
            logger.info("Retrieving report by ID: {}", id);
            EntityResponse<Report> report = entityService.getById(id, Report.class);
            if (report == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to retrieve report {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/business/{reportId}")
    public ResponseEntity<EntityResponse<Report>> getReportByBusinessId(@PathVariable String reportId) {
        try {
            logger.info("Retrieving report by business ID: {}", reportId);
            EntityResponse<Report> report = entityService.findByBusinessId(Report.class, reportId, "reportId");
            if (report == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to retrieve report by business ID {}: {}", reportId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Report>> createReport(@RequestBody Report report) {
        try {
            logger.info("Creating new report with ID: {}", report.getReportId());
            EntityResponse<Report> savedReport = entityService.save(report);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedReport);
        } catch (Exception e) {
            logger.error("Failed to create report: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Report>> updateReport(
            @PathVariable UUID id,
            @RequestBody Report report,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating report {} with transition: {}", id, transition);
            EntityResponse<Report> updatedReport = entityService.update(id, report, transition);
            return ResponseEntity.ok(updatedReport);
        } catch (Exception e) {
            logger.error("Failed to update report {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID id) {
        try {
            logger.info("Deleting report: {}", id);
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete report {}: {}", id, e.getMessage(), e);
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
