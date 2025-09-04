package com.java_template.application.controller;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private EntityService entityService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public ResponseEntity<Map<String, Object>> createReport(@RequestBody Map<String, Object> request) {
        try {
            String reportName = (String) request.get("reportName");
            String reportType = (String) request.get("reportType");
            String dateRange = (String) request.get("dateRange");
            Object filterCriteria = request.get("filterCriteria");

            if (reportName == null || reportType == null) {
                return createErrorResponse("Report name and type are required", HttpStatus.BAD_REQUEST);
            }

            // Create a new report entity
            Report report = new Report();
            report.setReportId("RPT-" + System.currentTimeMillis());
            report.setReportName(reportName);
            report.setReportType(reportType);
            report.setDateRange(dateRange);
            
            if (filterCriteria != null) {
                report.setFilterCriteria(objectMapper.writeValueAsString(filterCriteria));
            }

            // Save the report entity (this will trigger the start_collection transition)
            EntityResponse<Report> savedReport = entityService.save(report);

            Map<String, Object> responseData = createReportResponseData(savedReport);
            return createSuccessResponse(responseData, "Report creation initiated");

        } catch (Exception e) {
            logger.error("Error creating report: {}", e.getMessage(), e);
            return createErrorResponse("Failed to create report: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllReports() {
        try {
            List<EntityResponse<Report>> reports = entityService.findAll(Report.class);
            
            List<Map<String, Object>> reportData = reports.stream()
                .map(this::createReportResponseData)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", reportData);
            response.put("count", reportData.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving reports: {}", e.getMessage(), e);
            return createErrorResponse("Failed to retrieve reports: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReportById(@PathVariable String id) {
        try {
            UUID reportUuid = UUID.fromString(id);
            EntityResponse<Report> report = entityService.getById(reportUuid, Report.class);

            if (report == null) {
                return createErrorResponse("Report not found", HttpStatus.NOT_FOUND);
            }

            Map<String, Object> responseData = createReportResponseData(report);
            return createSuccessResponse(responseData, null);

        } catch (IllegalArgumentException e) {
            return createErrorResponse("Invalid report ID format", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error retrieving report {}: {}", id, e.getMessage(), e);
            return createErrorResponse("Failed to retrieve report: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/filtered")
    public ResponseEntity<Map<String, Object>> generateFilteredReport(@RequestBody Map<String, Object> request) {
        try {
            String reportName = (String) request.get("reportName");
            String reportType = (String) request.get("reportType");
            Object filterCriteria = request.get("filterCriteria");

            if (reportName == null) {
                return createErrorResponse("Report name is required", HttpStatus.BAD_REQUEST);
            }

            // Create a new filtered report entity
            Report report = new Report();
            report.setReportId("RPT-" + System.currentTimeMillis());
            report.setReportName(reportName);
            report.setReportType(reportType != null ? reportType : "FILTERED");
            
            if (filterCriteria != null) {
                report.setFilterCriteria(objectMapper.writeValueAsString(filterCriteria));
            }

            // Save the report entity (this will trigger the start_collection transition)
            EntityResponse<Report> savedReport = entityService.save(report);

            Map<String, Object> responseData = createReportResponseData(savedReport);
            return createSuccessResponse(responseData, "Filtered report creation initiated");

        } catch (Exception e) {
            logger.error("Error creating filtered report: {}", e.getMessage(), e);
            return createErrorResponse("Failed to create filtered report: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryFailedReport(@PathVariable String id) {
        try {
            UUID reportUuid = UUID.fromString(id);

            // Get the current report
            EntityResponse<Report> currentReport = entityService.getById(reportUuid, Report.class);
            if (currentReport == null) {
                return createErrorResponse("Report not found", HttpStatus.NOT_FOUND);
            }

            // Update the report with retry_generation transition
            EntityResponse<Report> updatedReport = entityService.update(reportUuid, currentReport.getData(), "retry_generation");

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("id", updatedReport.getId().toString());
            responseData.put("reportId", updatedReport.getData().getReportId());
            responseData.put("state", updatedReport.getState());

            return createSuccessResponse(responseData, "Report retry initiated");

        } catch (IllegalArgumentException e) {
            return createErrorResponse("Invalid report ID format", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error retrying report {}: {}", id, e.getMessage(), e);
            return createErrorResponse("Failed to retry report: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getReportSummary() {
        try {
            List<EntityResponse<Report>> allReports = entityService.findAll(Report.class);
            List<EntityResponse<Booking>> allBookings = entityService.findAll(Booking.class);

            long totalReports = allReports.size();
            long completedReports = allReports.stream()
                .filter(report -> "completed".equals(report.getState()))
                .count();
            long failedReports = allReports.stream()
                .filter(report -> "failed".equals(report.getState()))
                .count();
            long inProgressReports = totalReports - completedReports - failedReports;

            long totalBookingsProcessed = allBookings.stream()
                .filter(booking -> "processed".equals(booking.getState()))
                .count();

            BigDecimal totalRevenueReported = allReports.stream()
                .filter(report -> "completed".equals(report.getState()))
                .map(EntityResponse::getData)
                .filter(report -> report.getTotalRevenue() != null)
                .map(Report::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("totalReports", totalReports);
            summaryData.put("completedReports", completedReports);
            summaryData.put("failedReports", failedReports);
            summaryData.put("inProgressReports", inProgressReports);
            summaryData.put("totalBookingsProcessed", totalBookingsProcessed);
            summaryData.put("totalRevenueReported", totalRevenueReported);

            return createSuccessResponse(summaryData, null);

        } catch (Exception e) {
            logger.error("Error generating report summary: {}", e.getMessage(), e);
            return createErrorResponse("Failed to generate report summary: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> createReportResponseData(EntityResponse<Report> reportResponse) {
        Map<String, Object> data = new HashMap<>();
        Report report = reportResponse.getData();
        
        data.put("id", reportResponse.getId() != null ? reportResponse.getId().toString() : null);
        data.put("reportId", report.getReportId());
        data.put("reportName", report.getReportName());
        data.put("reportType", report.getReportType());
        data.put("dateRange", report.getDateRange());
        data.put("filterCriteria", report.getFilterCriteria());
        data.put("totalBookings", report.getTotalBookings());
        data.put("totalRevenue", report.getTotalRevenue());
        data.put("averagePrice", report.getAveragePrice());
        data.put("depositPaidCount", report.getDepositPaidCount());
        data.put("depositUnpaidCount", report.getDepositUnpaidCount());
        data.put("generatedAt", report.getGeneratedAt());
        data.put("generatedBy", report.getGeneratedBy());
        data.put("state", reportResponse.getState());
        
        return data;
    }

    private ResponseEntity<Map<String, Object>> createSuccessResponse(Object data, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        if (message != null) {
            response.put("message", message);
        }
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}
