package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/activities")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, DailyReport> dailyReports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String EXTERNAL_ACTIVITY_API = "https://fakerestapi.azurewebsites.net/api/v1/Activities";

    @Data @NoArgsConstructor @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class DailyReportSummary {
        private int totalUsers;
        private int totalActivities;
        private String mostFrequentActivity;
        private double averageActivitiesPerUser;
        private List<Anomaly> anomaliesDetected;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class Anomaly {
        private int userId;
        private String activity;
        private String note;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class DailyReport {
        private String date;
        private DailyReportSummary summary;
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestActivities() {
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        logger.info("Received ingest request, jobId={}", jobId);
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));

        CompletableFuture.runAsync(() -> {
            try {
                processIngestionJob(jobId);
                entityJobs.put(jobId, new JobStatus("done", requestedAt));
            } catch (Exception e) {
                logger.error("Error during ingestion jobId={}", jobId, e);
                entityJobs.put(jobId, new JobStatus("failed", requestedAt));
            }
        });

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "jobId", jobId,
                "message", "Data ingestion started"
        ));
    }

    @GetMapping("/report/daily")
    public ResponseEntity<DailyReport> getLatestDailyReport() {
        if (dailyReports.isEmpty()) {
            logger.info("No daily reports found");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports available");
        }
        String latestDate = dailyReports.keySet().stream().max(String::compareTo).get();
        DailyReport report = dailyReports.get(latestDate);
        logger.info("Returning daily report for date {}", latestDate);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/report/history")
    public ResponseEntity<List<DailyReport>> getHistoricalReports(
            @RequestParam @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startDate,
            @RequestParam @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endDate) {

        logger.info("Fetching reports from {} to {}", startDate, endDate);

        if (startDate.compareTo(endDate) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be <= endDate");
        }

        List<DailyReport> reports = new ArrayList<>();
        for (String date : dailyReports.keySet()) {
            if (date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0) {
                reports.add(dailyReports.get(date));
            }
        }
        reports.sort(Comparator.comparing(DailyReport::getDate));
        return ResponseEntity.ok(reports);
    }

    private void processIngestionJob(String jobId) {
        logger.info("Starting ingestion jobId={}", jobId);
        JsonNode activitiesData = fetchExternalActivities();
        DailyReport report = analyzeActivities(activitiesData);
        dailyReports.put(report.getDate(), report);
        logger.info("Stored daily report for date {}", report.getDate());
        sendReportEmail(report);
        logger.info("Ingestion jobId={} completed", jobId);
    }

    private JsonNode fetchExternalActivities() {
        try {
            logger.info("Fetching activities from Fakerest API: {}", EXTERNAL_ACTIVITY_API);
            String json = restTemplate.getForObject(EXTERNAL_ACTIVITY_API, String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            logger.error("Failed to fetch activities from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch external activities");
        }
    }

    private DailyReport analyzeActivities(JsonNode activitiesData) {
        logger.info("Analyzing {} activities", activitiesData.size());
        Map<Integer, Integer> userActivityCount = new HashMap<>();
        Map<String, Integer> activityTypeCount = new HashMap<>();
        Random random = new Random();

        for (JsonNode activity : activitiesData) {
            int userId = random.nextInt(10) + 1;
            String activityTitle = activity.path("title").asText("unknown");
            userActivityCount.put(userId, userActivityCount.getOrDefault(userId, 0) + 1);
            activityTypeCount.put(activityTitle, activityTypeCount.getOrDefault(activityTitle, 0) + 1);
        }

        int totalUsers = userActivityCount.size();
        int totalActivities = activitiesData.size();
        String mostFrequentActivity = activityTypeCount.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");
        double averageActivitiesPerUser = totalUsers > 0 ? (double) totalActivities / totalUsers : 0;
        List<Anomaly> anomalies = new ArrayList<>();
        double anomalyThreshold = averageActivitiesPerUser * 1.5;
        for (Map.Entry<Integer, Integer> entry : userActivityCount.entrySet()) {
            if (entry.getValue() > anomalyThreshold) {
                anomalies.add(new Anomaly(entry.getKey(), "Multiple activities", "Unusually high frequency"));
            }
        }

        DailyReportSummary summary = new DailyReportSummary(
                totalUsers,
                totalActivities,
                mostFrequentActivity,
                averageActivitiesPerUser,
                anomalies
        );

        String today = Instant.now().toString().substring(0, 10);
        return new DailyReport(today, summary);
    }

    private void sendReportEmail(DailyReport report) {
        // TODO: Replace with real email sending logic integrated with email service/provider
        logger.info("Sending daily report email to admin: {}", ADMIN_EMAIL);
        logger.info("Report date: {}", report.getDate());
        logger.info("Summary: totalUsers={}, totalActivities={}, mostFrequentActivity={}",
                report.getSummary().getTotalUsers(),
                report.getSummary().getTotalActivities(),
                report.getSummary().getMostFrequentActivity());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        Map<String, String> error = Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}