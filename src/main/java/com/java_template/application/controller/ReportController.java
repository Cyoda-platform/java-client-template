package com.java_template.application.controller;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<EntityResponse<Report>> createReport(@RequestBody Report report) {
        try {
            logger.info("Creating new report: {}", report.getReportName());
            EntityResponse<Report> response = entityService.save(report);
            logger.info("Report created with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to create report: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Report>> getReport(@PathVariable UUID id) {
        try {
            logger.info("Retrieving report with ID: {}", id);
            EntityResponse<Report> response = entityService.getItem(id, Report.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve report {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Report>>> getAllReports() {
        try {
            logger.info("Retrieving all reports");
            List<EntityResponse<Report>> reports = entityService.getItems(
                Report.class,
                Report.ENTITY_NAME,
                Report.ENTITY_VERSION,
                null,
                null,
                null
            );
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Failed to retrieve reports: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<EntityResponse<Report>>> searchReports(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String fileFormat,
            @RequestParam(required = false) LocalDate periodStart,
            @RequestParam(required = false) LocalDate periodEnd,
            @RequestParam(required = false) String state) {
        try {
            logger.info("Searching reports with filters - type: {}, format: {}, period: {} to {}, state: {}", 
                       reportType, fileFormat, periodStart, periodEnd, state);
            
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            
            List<Condition> conditions = new java.util.ArrayList<>();
            
            if (reportType != null && !reportType.trim().isEmpty()) {
                conditions.add(Condition.of("$.reportType", "EQUALS", reportType));
            }
            if (fileFormat != null && !fileFormat.trim().isEmpty()) {
                conditions.add(Condition.of("$.fileFormat", "EQUALS", fileFormat));
            }
            if (periodStart != null) {
                conditions.add(Condition.of("$.reportPeriodStart", "GREATER_THAN_OR_EQUAL", periodStart.toString()));
            }
            if (periodEnd != null) {
                conditions.add(Condition.of("$.reportPeriodEnd", "LESS_THAN_OR_EQUAL", periodEnd.toString()));
            }
            if (state != null && !state.trim().isEmpty()) {
                conditions.add(Condition.lifecycle("state", "EQUALS", state));
            }
            
            condition.setConditions(conditions);
            
            List<EntityResponse<Report>> reports = entityService.getItemsByCondition(
                Report.class,
                Report.ENTITY_NAME,
                Report.ENTITY_VERSION,
                condition,
                true
            );
            
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Failed to search reports: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Report>> updateReport(
            @PathVariable UUID id, 
            @RequestBody Report report,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating report with ID: {}, transition: {}", id, transition);
            
            EntityResponse<Report> response = entityService.update(id, report, transition);
            
            logger.info("Report updated with ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update report {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID id) {
        try {
            logger.info("Deleting report with ID: {}", id);
            entityService.deleteById(id);
            logger.info("Report deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete report {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/transitions/{transitionName}")
    public ResponseEntity<EntityResponse<Report>> transitionReport(
            @PathVariable UUID id, 
            @PathVariable String transitionName) {
        try {
            logger.info("Transitioning report {} with transition: {}", id, transitionName);
            
            // Get current report
            EntityResponse<Report> currentResponse = entityService.getItem(id, Report.class);
            Report report = currentResponse.getData();
            
            // Update with transition
            EntityResponse<Report> response = entityService.update(id, report, transitionName);
            
            logger.info("Report transitioned with ID: {}, new state: {}", response.getId(), response.getState());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to transition report {} with {}: {}", id, transitionName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<EntityResponse<Report>> generateWeeklyReport() {
        try {
            logger.info("Generating weekly performance report");
            
            // Create new report entity
            Report report = new Report();
            report.setReportName("Weekly Performance Report");
            report.setReportType("WEEKLY_SUMMARY");
            report.setFileFormat("PDF");
            
            // Set report period to last week
            LocalDate now = LocalDate.now();
            LocalDate lastMonday = now.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
            LocalDate lastSunday = lastMonday.plusDays(6);
            report.setReportPeriodStart(lastMonday);
            report.setReportPeriodEnd(lastSunday);
            
            EntityResponse<Report> response = entityService.save(report);
            logger.info("Weekly report generation initiated with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to generate weekly report: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<Report>> getReportByBusinessId(@PathVariable Long businessId) {
        try {
            logger.info("Retrieving report with business ID: {}", businessId);
            EntityResponse<Report> response = entityService.findByBusinessId(
                Report.class,
                Report.ENTITY_NAME,
                Report.ENTITY_VERSION,
                businessId.toString(),
                "id"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve report by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<Report>> updateReportByBusinessId(
            @PathVariable Long businessId, 
            @RequestBody Report report,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating report with business ID: {}, transition: {}", businessId, transition);
            
            EntityResponse<Report> response = entityService.updateByBusinessId(report, "id", transition);
            
            logger.info("Report updated with business ID: {}", businessId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update report by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
