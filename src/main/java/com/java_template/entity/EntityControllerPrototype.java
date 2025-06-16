package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/activities")
public class EntityControllerPrototype {

    private static final String FAKEREST_API_ACTIVITIES_URL = "https://fakerestapi.azurewebsites.net/api/v1/Activities";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ActivityReport> reports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/ingest")
    public ResponseEntity<GenericResponse> ingestActivities(@RequestBody @Valid IngestRequest ingestRequest) {
        String dateStr = ingestRequest.getDate() != null ? ingestRequest.getDate() : LocalDate.now().toString();
        log.info("Received ingestion request for date {}", dateStr);
        String jobId = "ingest-" + dateStr + "-" + OffsetDateTime.now().toEpochSecond();
        entityJobs.put(jobId, new JobStatus("processing", OffsetDateTime.now()));
        CompletableFuture.runAsync(() -> {
            try {
                ingestAndProcessData(dateStr);
                entityJobs.put(jobId, new JobStatus("completed", OffsetDateTime.now()));
                log.info("Ingestion and processing completed for date {}", dateStr);
            } catch (Exception e) {
                entityJobs.put(jobId, new JobStatus("failed", OffsetDateTime.now()));
                log.error("Error during ingestion for date {}: {}", dateStr, e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(new GenericResponse("success", "Data ingestion and processing started for date " + dateStr));
    }

    @GetMapping("/report")
    public ResponseEntity<ActivityReport> getReport(
        @RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date) {
        log.info("Fetching report for date {}", date);
        ActivityReport report = reports.get(date);
        if (report == null) {
            log.warn("Report not found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }
        return ResponseEntity.ok(report);
    }

    @PostMapping("/report/send")
    public ResponseEntity<GenericResponse> sendReportEmail(@RequestBody @Valid SendReportRequest request) {
        String date = request.getDate();
        String adminEmail = request.getAdminEmail();
        log.info("Request to send report for date {} to admin {}", date, adminEmail);
        ActivityReport report = reports.get(date);
        if (report == null) {
            log.warn("Report not found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending report email to {}", adminEmail);
                Thread.sleep(1000);
                log.info("Report email sent to {}", adminEmail);
            } catch (InterruptedException e) {
                log.error("Error sending report email: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        });
        return ResponseEntity.ok(new GenericResponse("success", "Report for " + date + " sent to " + adminEmail));
    }

    private void ingestAndProcessData(String dateStr) {
        log.info("Fetching activity data from Fakerest API...");
        try {
            URI uri = new URI(FAKEREST_API_ACTIVITIES_URL);
            String rawJson = restTemplate.getForObject(uri, String.class);
            if (rawJson == null) {
                throw new IllegalStateException("Received null response from Fakerest API");
            }
            JsonNode rootNode = objectMapper.readTree(rawJson);
            int totalActivities = rootNode.isArray() ? rootNode.size() : 0;
            Map<String, Integer> activityTypesCount = new ConcurrentHashMap<>();
            activityTypesCount.put("typeA", 0);
            activityTypesCount.put("typeB", 0);
            activityTypesCount.put("typeC", 0);
            if (rootNode.isArray()) {
                for (JsonNode activityNode : rootNode) {
                    String activityName = activityNode.path("activityName").asText("");
                    int mod = activityName.length() % 3;
                    switch (mod) {
                        case 0 -> activityTypesCount.computeIfPresent("typeA", (k, v) -> v + 1);
                        case 1 -> activityTypesCount.computeIfPresent("typeB", (k, v) -> v + 1);
                        default -> activityTypesCount.computeIfPresent("typeC", (k, v) -> v + 1);
                    }
                }
            }
            ActivityReport report = new ActivityReport();
            report.setDate(dateStr);
            report.setTotalActivities(totalActivities);
            report.setActivityTypes(activityTypesCount);
            report.setTrends(Map.of("mostActiveUser", "user123", "peakActivityHour", "15:00"));
            report.setAnomalies(new String[]{"User456 showed unusually high activity"});
            reports.put(dateStr, report);
            log.info("Processed and stored report for date {}", dateStr);
        } catch (Exception e) {
            log.error("Failed to ingest and process data: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to ingest and process data: " + e.getMessage());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestRequest {
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendReportRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String date;
        @NotBlank
        @Email
        private String adminEmail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenericResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityReport {
        private String date;
        private int totalActivities;
        private Map<String, Integer> activityTypes;
        private Map<String, String> trends;
        private String[] anomalies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private OffsetDateTime timestamp;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<GenericResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(new GenericResponse("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new GenericResponse("error", "Internal server error"));
    }
}