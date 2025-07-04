package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, DailyReport> dailyReports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private static final String FAKERES_API_ACTIVITIES_URL = "https://fakerestapi.azurewebsites.net/api/v1/Activities";

    @PostMapping("/activities/fetch")
    public ResponseEntity<FetchResponse> fetchAndProcessActivities(@RequestBody @Valid FetchRequest request) {
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        log.info("Received fetch request for date {} with jobId {}", request.getDate(), jobId);
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));

        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting data ingestion for date {}", request.getDate());
                URI uri = URI.create(FAKERES_API_ACTIVITIES_URL);
                String jsonResponse = restTemplate.getForObject(uri, String.class);
                JsonNode activitiesNode = objectMapper.readTree(jsonResponse);

                Map<Integer, UserActivitySummary> userSummaries = new HashMap<>();
                if (activitiesNode.isArray()) {
                    for (JsonNode activity : activitiesNode) {
                        int userId = activity.path("userId").asInt(-1);
                        String activityName = activity.path("name").asText("unknown");
                        if (userId < 0) {
                            log.warn("Skipping activity without userId: {}", activity);
                            continue;
                        }
                        UserActivitySummary summary = userSummaries.computeIfAbsent(userId, k -> new UserActivitySummary());
                        summary.totalActivities++;
                        summary.activityTypes.merge(activityName.toLowerCase(), 1, Integer::sum);
                    }
                } else {
                    log.warn("Unexpected response format: expected array");
                }

                userSummaries.forEach((userId, summary) -> {
                    if (summary.totalActivities > 10) {
                        summary.anomalies.add("Unusually high activity count");
                    }
                });

                DailyReport report = new DailyReport(request.getDate(), new ArrayList<>());
                userSummaries.forEach((uid, summ) -> report.getReports().add(new UserReport(uid, summ)));
                dailyReports.put(request.getDate(), report);
                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                log.info("Completed processing for date {}", request.getDate());
            } catch (Exception e) {
                log.error("Error processing date {}: {}", request.getDate(), e.getMessage(), e);
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
            }
        });

        return ResponseEntity.ok(new FetchResponse("success",
                "Activities fetched, processed, and report generation started for " + request.getDate()));
    }

    @GetMapping("/reports/daily/{date}")
    public ResponseEntity<DailyReport> getDailyReport(
            @PathVariable @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String date) {
        log.info("Received request for report date {}", date);
        DailyReport report = dailyReports.get(date);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date: " + date);
        }
        return ResponseEntity.ok(report);
    }

    @PostMapping("/reports/send")
    public ResponseEntity<SendReportResponse> sendReport(@RequestBody @Valid SendReportRequest request) {
        log.info("Received send request for date {} to {}", request.getDate(), request.getAdminEmails());
        DailyReport report = dailyReports.get(request.getDate());
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date: " + request.getDate());
        }
        log.info("Simulating email send for date {} to {}", request.getDate(), request.getAdminEmails());
        return ResponseEntity.ok(new SendReportResponse("success",
                "Report for " + request.getDate() + " sent to " + String.join(", ", request.getAdminEmails())));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: {} - {}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FetchRequest {
        @NotBlank
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FetchResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SendReportRequest {
        @NotBlank
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}")
        private String date;
        @NotEmpty
        private List<@Email String> adminEmails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SendReportResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class JobStatus {
        private String status;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DailyReport {
        private String date;
        private List<UserReport> reports;
    }

    @Data
    @NoArgsConstructor
    private static class UserActivitySummary {
        private int totalActivities = 0;
        private Map<String, Integer> activityTypes = new HashMap<>();
        private List<String> anomalies = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class UserReport {
        private int userId;
        private UserActivitySummary activitySummary;
    }
}