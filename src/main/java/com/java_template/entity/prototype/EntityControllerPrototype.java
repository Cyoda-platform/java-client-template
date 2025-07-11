```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype/api/report")
@Validated
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final Map<String, ReportResult> reportsStore = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * POST /prototype/api/report/generate
     * Triggers the download, analysis, and email sending process.
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateReportResponse> generateReport(@RequestBody @Validated GenerateReportRequest request) {
        log.info("Received report generation request: dataUrl={}, subscribersCount={}, reportType={}",
                request.getDataUrl(), request.getSubscribers().size(), request.getReportType());

        if (!isValidUrl(request.getDataUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dataUrl");
        }
        if (request.getSubscribers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscribers list cannot be empty");
        }

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        entityJobs.put(jobId, new JobStatus("processing", requestedAt));
        // Fire and forget processing asynchronously
        CompletableFuture.runAsync(() -> processReportJob(jobId, request));

        GenerateReportResponse response = new GenerateReportResponse("processing", "Report generation started", jobId);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET /prototype/api/report/{reportId}
     * Returns the report status and results.
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportStatusResponse> getReportStatus(@PathVariable String reportId) {
        log.info("Fetching report status for reportId={}", reportId);
        JobStatus jobStatus = entityJobs.get(reportId);
        if (jobStatus == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report ID not found");
        }

        ReportResult result = reportsStore.get(reportId);

        ReportStatusResponse response = new ReportStatusResponse();
        response.setReportId(reportId);
        response.setGeneratedAt(jobStatus.getRequestedAt().toString());
        response.setStatus(jobStatus.getStatus());
        response.setReportSummary(result != null ? result.getSummary() : null);

        return ResponseEntity.ok(response);
    }

    // --- Internal processing ---

    private void processReportJob(String jobId, GenerateReportRequest request) {
        try {
            log.info("[{}] Starting download of CSV from {}", jobId, request.getDataUrl());
            String csvData = downloadCsv(request.getDataUrl());
            log.info("[{}] CSV data downloaded, length={}", jobId, csvData.length());

            Map<String, Object> analysisResult = analyzeCsvData(csvData, request.getReportType());
            log.info("[{}] Data analysis completed", jobId);

            // Store report summary for retrieval
            reportsStore.put(jobId, new ReportResult(analysisResult));

            // Fire and forget email sending
            CompletableFuture.runAsync(() -> sendReportEmail(jobId, request.getSubscribers(), analysisResult));

            entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
            log.info("[{}] Report processing completed successfully", jobId);
        } catch (Exception ex) {
            log.error("[{}] Error during report processing: {}", jobId, ex.getMessage(), ex);
            entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
            // TODO: Optionally store error details for GET endpoint
        }
    }

    private String downloadCsv(String url) {
        try {
            // Using RestTemplate to download CSV as plain text
            return restTemplate.getForObject(URI.create(url), String.class);
        } catch (Exception ex) {
            log.error("Failed to download CSV data from {}: {}", url, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to download CSV data");
        }
    }

    /**
     * Analyze CSV data according to reportType.
     * This is a placeholder for actual analysis logic (e.g. pandas equivalent in Java).
     * 
     * @param csvData Raw CSV string content
     * @param reportType Type of report requested
     * @return Map representing summary results
     */
    private Map<String, Object> analyzeCsvData(String csvData, String reportType) {
        // TODO: Replace with real data parsing and analysis logic
        // For prototype: parse CSV lines count and mock some results

        Map<String, Object> result = new HashMap<>();
        String[] lines = csvData.split("\\r?\\n");
        result.put("totalRows", lines.length - 1); // exclude header line
        result.put("reportType", reportType);
        result.put("generatedAt", Instant.now().toString());
        result.put("sampleData", lines.length > 1 ? lines[1] : "No data");

        return result;
    }

    /**
     * Send report email to subscribers.
     * Placeholder method - just logs info.
     */
    private void sendReportEmail(String jobId, List<String> subscribers, Map<String, Object> analysisResult) {
        // TODO: Replace with real email sending logic
        log.info("[{}] Sending report email to {} subscribers. Report summary: {}", jobId, subscribers.size(), analysisResult);
        // Simulate email sending delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) { }
        log.info("[{}] Email sent successfully to subscribers", jobId);
    }

    // --- Utility methods ---

    private boolean isValidUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"));
        } catch (Exception e) {
            return false;
        }
    }

    // --- Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        log.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // --- DTOs ---

    @Data
    public static class GenerateReportRequest {
        @NotBlank
        private String dataUrl;

        @NotBlank
        private String reportType;

        @Email(message = "Invalid email in subscribers")
        private List<@Email String> subscribers;
    }

    @Data
    @RequiredArgsConstructor
    public static class JobStatus {
        private final String status;
        private final Instant requestedAt;
    }

    @Data
    public static class GenerateReportResponse {
        private final String status;
        private final String message;
        private final String reportId;
    }

    @Data
    public static class ReportResult {
        private final Map<String, Object> summary;
    }

    @Data
    public static class ReportStatusResponse {
        private String reportId;
        private String generatedAt;
        private String status;
        private Map<String, Object> reportSummary;
    }
}
```