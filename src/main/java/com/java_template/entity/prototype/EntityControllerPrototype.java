package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype/api/report")
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final Map<String, ReportResult> reportsStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/generate")
    public ResponseEntity<GenerateReportResponse> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        log.info("Received report generation request: dataUrl={}, subscribersCount={}, reportType={}",
                request.getDataUrl(), request.getSubscribers().size(), request.getReportType());

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));

        CompletableFuture.runAsync(() -> processReportJob(jobId, request)); // fire-and-forget

        GenerateReportResponse response = new GenerateReportResponse("processing", "Report generation started", jobId);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ReportStatusResponse> getReportStatus(@PathVariable @NotBlank String reportId) {
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

    private void processReportJob(String jobId, GenerateReportRequest request) {
        try {
            log.info("[{}] Downloading CSV from {}", jobId, request.getDataUrl());
            String csvData = restTemplate.getForObject(URI.create(request.getDataUrl()), String.class);
            log.info("[{}] CSV downloaded, length={}", jobId, csvData.length());

            Map<String, Object> analysisResult = analyzeCsvData(csvData, request.getReportType());
            reportsStore.put(jobId, new ReportResult(analysisResult));

            CompletableFuture.runAsync(() -> sendReportEmail(jobId, request.getSubscribers(), analysisResult)); // fire-and-forget

            entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
            log.info("[{}] Report processing completed", jobId);
        } catch (Exception ex) {
            log.error("[{}] Error processing report: {}", jobId, ex.getMessage(), ex);
            entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
        }
    }

    private Map<String, Object> analyzeCsvData(String csvData, String reportType) {
        // TODO: Replace with real parsing and analysis logic
        Map<String, Object> result = new HashMap<>();
        String[] lines = csvData.split("\\r?\\n");
        result.put("totalRows", lines.length - 1);
        result.put("reportType", reportType);
        result.put("generatedAt", Instant.now().toString());
        result.put("sampleData", lines.length > 1 ? lines[1] : "No data");
        return result;
    }

    private void sendReportEmail(String jobId, List<String> subscribers, Map<String, Object> analysisResult) {
        // TODO: Integrate real email service
        log.info("[{}] Sending report to {} subscribers: {}", jobId, subscribers.size(), analysisResult);
        try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
        log.info("[{}] Email sent", jobId);
    }

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

    @Data
    public static class GenerateReportRequest {
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "dataUrl must start with http:// or https://")
        private String dataUrl;

        @NotBlank
        private String reportType;

        @NotEmpty
        private List<@Email(message = "Invalid email address") String> subscribers;
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