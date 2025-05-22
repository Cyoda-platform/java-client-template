```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final Map<String, ReportResult> reports = new ConcurrentHashMap<>();

    private final List<String> staticSubscribers = List.of(
            "user1@example.com",
            "user2@example.com"
            // TODO: Replace with actual subscriber source if needed
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class AnalyzeRequest {
        private String csvUrl;
    }

    @Data
    @AllArgsConstructor
    static class AnalyzeResponse {
        private String taskId;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportResult {
        private String taskId;
        private String status; // completed | pending | failed
        private SummaryStatistics summaryStatistics;
        private BasicTrends basicTrends;
        private boolean emailSent;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class SummaryStatistics {
        private Double meanPrice;
        private Double medianPrice;
        private Integer totalListings;
        // Additional fields may be added here
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class BasicTrends {
        private String priceTrend; // e.g. "increasing", "stable"
        // Additional fields may be added here
    }

    @Data
    @AllArgsConstructor
    static class SubscribersResponse {
        private List<String> subscribers;
    }

    @PostMapping("/data/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeData(@RequestBody AnalyzeRequest request) {
        log.info("Received analyze request for CSV URL: {}", request.getCsvUrl());
        if (request.getCsvUrl() == null || request.getCsvUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csvUrl must be provided");
        }

        String taskId = UUID.randomUUID().toString();
        entityJobs.put(taskId, new JobStatus("processing", Instant.now()));

        // Fire-and-forget analysis task
        CompletableFuture.runAsync(() -> processAnalysis(taskId, request.getCsvUrl()), executor);

        return ResponseEntity.ok(new AnalyzeResponse(taskId, "started"));
    }

    @GetMapping("/report/{taskId}")
    public ResponseEntity<ReportResult> getReport(@PathVariable String taskId) {
        log.info("Fetching report for taskId: {}", taskId);
        ReportResult report = reports.get(taskId);
        if (report == null) {
            JobStatus jobStatus = entityJobs.get(taskId);
            if (jobStatus == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task ID not found");
            }
            // Report not ready yet, return pending status
            report = new ReportResult(taskId, jobStatus.getStatus(), null, null, false);
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        log.info("Returning static subscribers list");
        return ResponseEntity.ok(new SubscribersResponse(staticSubscribers));
    }

    private void processAnalysis(String taskId, String csvUrl) {
        try {
            log.info("Starting analysis for taskId: {} with CSV URL: {}", taskId, csvUrl);

            // 1) Download CSV data (as raw text)
            String csvData = downloadCsv(csvUrl);

            // 2) Parse and analyze CSV data
            // TODO: Replace with real CSV parsing and analysis
            SummaryStatistics stats = mockAnalyzeCsv(csvData);

            // 3) Determine basic trends
            BasicTrends trends = mockDetermineTrends(stats);

            // 4) Generate report and store result
            ReportResult report = new ReportResult(taskId, "completed", stats, trends, false);
            reports.put(taskId, report);

            // 5) Send email report to static subscribers (fire and forget)
            sendEmailReportAsync(taskId, report);

            // Update job status
            entityJobs.put(taskId, new JobStatus("completed", Instant.now()));
            log.info("Analysis and email completed for taskId: {}", taskId);

        } catch (Exception e) {
            log.error("Error processing analysis for taskId {}: {}", taskId, e.getMessage(), e);
            entityJobs.put(taskId, new JobStatus("failed", Instant.now()));
            reports.put(taskId, new ReportResult(taskId, "failed", null, null, false));
        }
    }

    private String downloadCsv(String csvUrl) throws Exception {
        log.info("Downloading CSV from URL: {}", csvUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(csvUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to download CSV, status code: " + response.statusCode());
        }
        return response.body();
    }

    // Mock analysis: parse CSV string and return dummy summary stats
    private SummaryStatistics mockAnalyzeCsv(String csvData) {
        log.info("Mock analyzing CSV data (length={} chars)", csvData.length());

        // TODO: Replace this mock with real CSV parsing and analysis logic
        return new SummaryStatistics(500000.0, 450000.0, 1000);
    }

    // Mock trend determination based on summary stats
    private BasicTrends mockDetermineTrends(SummaryStatistics stats) {
        log.info("Mock determining trends based on summary statistics");
        String trend = "stable"; // placeholder logic
        if (stats.getMeanPrice() != null && stats.getMeanPrice() > 600000) {
            trend = "increasing";
        }
        return new BasicTrends(trend);
    }

    @Async
    private void sendEmailReportAsync(String taskId, ReportResult report) {
        // TODO: Replace this mock with real email sending logic
        log.info("Mock sending email report for taskId: {} to subscribers: {}", taskId, staticSubscribers);

        // Simulate sending email by waiting a moment
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }

        // Update report to mark email as sent
        report.setEmailSent(true);
        reports.put(taskId, report);
        log.info("Mock email sent for taskId: {}", taskId);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
```
