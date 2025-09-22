package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.weekly_report.version_1.WeeklyReport;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ReportController - REST API for weekly report management and analytics
 * 
 * This controller provides endpoints for:
 * - Creating and generating weekly reports
 * - Retrieving report data and analytics
 * - Publishing and archiving reports
 * - Managing report generation schedules
 */
@RestController
@RequestMapping("/ui/reports")
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
     * Create a new weekly report
     */
    @PostMapping("/weekly")
    public ResponseEntity<EntityWithMetadata<WeeklyReport>> createWeeklyReport(@RequestBody ReportRequest reportRequest) {
        logger.info("Creating weekly report for week {} of {}", reportRequest.getWeekNumber(), reportRequest.getYear());

        try {
            // Create WeeklyReport entity
            WeeklyReport report = new WeeklyReport();
            report.setReportId(UUID.randomUUID().toString());
            report.setWeekNumber(reportRequest.getWeekNumber());
            report.setYear(reportRequest.getYear());
            
            // Calculate week start and end dates
            LocalDate[] weekDates = calculateWeekDates(reportRequest.getYear(), reportRequest.getWeekNumber());
            report.setWeekStartDate(weekDates[0]);
            report.setWeekEndDate(weekDates[1]);
            
            report.setCreatedAt(LocalDateTime.now());

            // Initialize metadata
            WeeklyReport.ReportMetadata metadata = new WeeklyReport.ReportMetadata();
            metadata.setStatus("generating");
            metadata.setGeneratedBy("manual");
            report.setMetadata(metadata);

            // Create the report entity (triggers ReportInitializationProcessor)
            EntityWithMetadata<WeeklyReport> result = entityService.create(report);

            // Start analytics generation (triggers ReportAnalyticsProcessor)
            EntityWithMetadata<WeeklyReport> analyticsResult = entityService.updateByBusinessId(
                report, "reportId", "generate_analytics");

            logger.info("Weekly report created and analytics generation started with ID: {}", result.getId());
            return ResponseEntity.ok(analyticsResult);

        } catch (Exception e) {
            logger.error("Failed to create weekly report for week {} of {}", reportRequest.getWeekNumber(), reportRequest.getYear(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get weekly report by ID
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<EntityWithMetadata<WeeklyReport>> getReport(@PathVariable String reportId) {
        logger.info("Retrieving report with ID: {}", reportId);

        try {
            ModelSpec modelSpec = createWeeklyReportModelSpec();
            EntityWithMetadata<WeeklyReport> report = entityService.findByBusinessId(modelSpec, reportId, "reportId", WeeklyReport.class);

            if (report != null) {
                return ResponseEntity.ok(report);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve report with ID: {}", reportId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get weekly report by week and year
     */
    @GetMapping("/weekly/{year}/{weekNumber}")
    public ResponseEntity<EntityWithMetadata<WeeklyReport>> getWeeklyReport(@PathVariable Integer year, 
                                                                           @PathVariable Integer weekNumber) {
        logger.info("Retrieving weekly report for week {} of {}", weekNumber, year);

        try {
            ModelSpec modelSpec = createWeeklyReportModelSpec();
            List<QueryCondition> conditions = new ArrayList<>();

            SimpleCondition yearCondition = new SimpleCondition()
                    .withJsonPath("$.year")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(year));
            conditions.add(yearCondition);

            SimpleCondition weekCondition = new SimpleCondition()
                    .withJsonPath("$.weekNumber")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(weekNumber));
            conditions.add(weekCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<WeeklyReport>> results = entityService.search(modelSpec, groupCondition, WeeklyReport.class);

            if (!results.isEmpty()) {
                return ResponseEntity.ok(results.get(0));
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve weekly report for week {} of {}", weekNumber, year, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Complete report generation
     */
    @PostMapping("/{reportId}/complete")
    public ResponseEntity<UUID> completeReport(@PathVariable String reportId) {
        logger.info("Completing report generation for: {}", reportId);

        try {
            ModelSpec modelSpec = createWeeklyReportModelSpec();
            
            // Get current report
            EntityWithMetadata<WeeklyReport> reportEntity = entityService.findByBusinessId(modelSpec, reportId, WeeklyReport.class);
            if (reportEntity == null) {
                return ResponseEntity.notFound().build();
            }

            WeeklyReport report = reportEntity.entity();

            // Complete generation with manual transition
            EntityWithMetadata<WeeklyReport> result = entityService.updateWithManualTransition(
                modelSpec, reportId, report, "complete_generation");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to complete report generation for: {}", reportId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Publish a completed report
     */
    @PostMapping("/{reportId}/publish")
    public ResponseEntity<UUID> publishReport(@PathVariable String reportId) {
        logger.info("Publishing report: {}", reportId);

        try {
            ModelSpec modelSpec = createWeeklyReportModelSpec();
            
            // Get current report
            EntityWithMetadata<WeeklyReport> reportEntity = entityService.findByBusinessId(modelSpec, reportId, WeeklyReport.class);
            if (reportEntity == null) {
                return ResponseEntity.notFound().build();
            }

            WeeklyReport report = reportEntity.entity();
            report.setPublishedAt(LocalDateTime.now());

            // Publish with manual transition
            EntityWithMetadata<WeeklyReport> result = entityService.updateWithManualTransition(
                modelSpec, reportId, report, "publish_report");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to publish report: {}", reportId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recent reports
     */
    @GetMapping("/recent")
    public ResponseEntity<List<EntityWithMetadata<WeeklyReport>>> getRecentReports(@RequestParam(defaultValue = "10") int limit) {
        logger.info("Retrieving {} recent reports", limit);

        try {
            ModelSpec modelSpec = createWeeklyReportModelSpec();
            List<QueryCondition> conditions = new ArrayList<>();

            // In a real implementation, you would add sorting by creation date
            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<WeeklyReport>> results = entityService.search(modelSpec, groupCondition, WeeklyReport.class);

            // Limit results (in a real implementation, this would be done in the query)
            if (results.size() > limit) {
                results = results.subList(0, limit);
            }

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Failed to retrieve recent reports", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get reports by year
     */
    @GetMapping("/year/{year}")
    public ResponseEntity<List<EntityWithMetadata<WeeklyReport>>> getReportsByYear(@PathVariable Integer year) {
        logger.info("Retrieving reports for year: {}", year);

        try {
            ModelSpec modelSpec = createWeeklyReportModelSpec();
            List<QueryCondition> conditions = new ArrayList<>();

            SimpleCondition yearCondition = new SimpleCondition()
                    .withJsonPath("$.year")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(year));
            conditions.add(yearCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<WeeklyReport>> results = entityService.search(modelSpec, groupCondition, WeeklyReport.class);

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Failed to retrieve reports for year: {}", year, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Archive a report
     */
    @PostMapping("/{reportId}/archive")
    public ResponseEntity<UUID> archiveReport(@PathVariable String reportId) {
        logger.info("Archiving report: {}", reportId);

        try {
            ModelSpec modelSpec = createWeeklyReportModelSpec();
            
            // Get current report
            EntityWithMetadata<WeeklyReport> reportEntity = entityService.findByBusinessId(modelSpec, reportId, WeeklyReport.class);
            if (reportEntity == null) {
                return ResponseEntity.notFound().build();
            }

            WeeklyReport report = reportEntity.entity();

            // Archive with manual transition
            EntityWithMetadata<WeeklyReport> result = entityService.updateWithManualTransition(
                modelSpec, reportId, report, "archive_report");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to archive report: {}", reportId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate current week report
     */
    @PostMapping("/current-week")
    public ResponseEntity<EntityWithMetadata<WeeklyReport>> generateCurrentWeekReport() {
        LocalDate now = LocalDate.now();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int weekNumber = now.get(weekFields.weekOfWeekBasedYear());
        int year = now.get(weekFields.weekBasedYear());

        logger.info("Generating report for current week {} of {}", weekNumber, year);

        ReportRequest request = new ReportRequest();
        request.setWeekNumber(weekNumber);
        request.setYear(year);

        return createWeeklyReport(request);
    }

    private ModelSpec createWeeklyReportModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("WeeklyReport");
        modelSpec.setVersion(1);
        return modelSpec;
    }

    private LocalDate[] calculateWeekDates(int year, int weekNumber) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDate firstDayOfYear = LocalDate.of(year, 1, 1);
        LocalDate firstDayOfWeek = firstDayOfYear.with(weekFields.weekOfWeekBasedYear(), weekNumber)
                                                 .with(weekFields.dayOfWeek(), 1);
        LocalDate lastDayOfWeek = firstDayOfWeek.plusDays(6);
        
        return new LocalDate[]{firstDayOfWeek, lastDayOfWeek};
    }

    /**
     * Request DTO for creating reports
     */
    @Getter
    @Setter
    public static class ReportRequest {
        private Integer weekNumber;
        private Integer year;
    }
}
