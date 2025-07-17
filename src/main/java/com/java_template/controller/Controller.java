package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/cyoda/api/jobs")
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private final Map<String, SummaryReport> latestReport = new ConcurrentHashMap<>();

    @PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> analyzeBooks(@RequestBody @Valid AnalyzeRequest request) {
        Instant triggerInstant = (request.getTriggerDate() != null && !request.getTriggerDate().isBlank())
                ? Instant.parse(request.getTriggerDate() + "T00:00:00Z")
                : Instant.now();

        logger.info("Received analyzeBooks request. Trigger date: {}", triggerInstant);
        String jobId = UUID.randomUUID().toString();
        latestReport.put("latest", new SummaryReport("processing", triggerInstant.toString()));

        // Persist job trigger or related entity if needed
        entityService.addItem(new JobTriggerEntity(jobId, triggerInstant.toString())); // example entity persistence

        CompletableFuture.runAsync(() -> {
            // The actual business logic moved to processors/workflows
            // Controller only triggers async process or delegates
            // No business logic here
        }).exceptionally(ex -> {
            logger.error("Error during book analysis jobId={} : {}", jobId, ex.getMessage(), ex);
            latestReport.put("latest", new SummaryReport("error", triggerInstant.toString()));
            return null;
        });

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Book data analysis started and report generation will be emailed.");
        response.put("jobId", jobId);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping(path = "/reports/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SummaryReport> getLatestReport() {
        SummaryReport report = latestReport.get("latest");
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        logger.error("Exception: {}", ex.getMessage(), ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Exception");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(500).body(error);
    }

    // DTO classes moved here from prototype for request/response mapping or replaced by entities if available

    public static class AnalyzeRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "triggerDate must be in YYYY-MM-DD format")
        private String triggerDate;

        public String getTriggerDate() {
            return triggerDate;
        }

        public void setTriggerDate(String triggerDate) {
            this.triggerDate = triggerDate;
        }
    }

    public static class SummaryReport {
        private String status;
        private String reportDate;

        public SummaryReport() {
        }

        public SummaryReport(String status, String reportDate) {
            this.status = status;
            this.reportDate = reportDate;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReportDate() {
            return reportDate;
        }

        public void setReportDate(String reportDate) {
            this.reportDate = reportDate;
        }
    }

    // Example entity for persistence, assuming such a class exists in entity package or create accordingly
    public static class JobTriggerEntity {
        private String jobId;
        private String triggerDate;

        public JobTriggerEntity(String jobId, String triggerDate) {
            this.jobId = jobId;
            this.triggerDate = triggerDate;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getTriggerDate() {
            return triggerDate;
        }

        public void setTriggerDate(String triggerDate) {
            this.triggerDate = triggerDate;
        }
    }
}