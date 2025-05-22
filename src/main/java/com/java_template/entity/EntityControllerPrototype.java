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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Validated
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
        @NotBlank
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
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class BasicTrends {
        private String priceTrend;
    }

    @Data
    @AllArgsConstructor
    static class SubscribersResponse {
        private List<String> subscribers;
    }

    @PostMapping("/data/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeData(@RequestBody @Valid AnalyzeRequest request) {
        log.info("Received analyze request for CSV URL: {}", request.getCsvUrl());
        if (request.getCsvUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csvUrl must be provided");
        }

        String taskId = UUID.randomUUID().toString();
        entityJobs.put(taskId, new JobStatus("processing", Instant.now()));

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
            String csvData = downloadCsv(csvUrl);
            SummaryStatistics stats = mockAnalyzeCsv(csvData);
            BasicTrends trends = mockDetermineTrends(stats);
            ReportResult report = new ReportResult(taskId, "completed", stats, trends, false);
            reports.put(taskId, report);
            sendEmailReportAsync(taskId, report);
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

    private SummaryStatistics mockAnalyzeCsv(String csvData) {
        log.info("Mock analyzing CSV data (length={} chars)", csvData.length());
        return new SummaryStatistics(500000.0, 450000.0, 1000);
    }

    private BasicTrends mockDetermineTrends(SummaryStatistics stats) {
        log.info("Mock determining trends based on summary statistics");
        String trend = "stable";
        if (stats.getMeanPrice() != null && stats.getMeanPrice() > 600000) {
            trend = "increasing";
        }
        return new BasicTrends(trend);
    }

    @Async
    private void sendEmailReportAsync(String taskId, ReportResult report) {
        log.info("Mock sending email report for taskId: {} to subscribers: {}", taskId, staticSubscribers);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
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