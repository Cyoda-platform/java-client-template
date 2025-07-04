package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, DailyReport> dailyReports = new ConcurrentHashMap<>();
    private static final String DEFAULT_ADMIN_EMAIL = "admin@example.com";
    private final List<SentEmail> sentEmails = Collections.synchronizedList(new ArrayList<>());

    @PostMapping("/activities/ingest")
    public ResponseEntity<IngestResponse> ingestActivities(@RequestBody @Valid IngestRequest request) {
        String date = Optional.ofNullable(request.getDate()).orElse(todayIsoDate());
        logger.info("Received ingestion request for date: {}", date);
        try {
            String fakerestUrl = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
            String response = restTemplate.getForObject(fakerestUrl, String.class);
            JsonNode activitiesNode = objectMapper.readTree(response);

            Map<String, Integer> activityTypeFrequency = new HashMap<>();
            int totalActivities = 0;
            if (activitiesNode.isArray()) {
                for (JsonNode activity : activitiesNode) {
                    totalActivities++;
                    String title = activity.path("Title").asText("Unknown");
                    activityTypeFrequency.merge(title, 1, Integer::sum);
                }
            }

            List<String> anomalies = new ArrayList<>();
            if (totalActivities == 0) {
                anomalies.add("No activities found for the date.");
            }
            if (activityTypeFrequency.values().stream().anyMatch(freq -> freq > 100)) {
                anomalies.add("Some activity type frequency unusually high.");
            }

            DailyReport report = new DailyReport(date, totalActivities,
                    new ArrayList<>(activityTypeFrequency.keySet()), anomalies);
            dailyReports.put(date, report);

            logger.info("Ingested {} activities for date {}", totalActivities, date);
            return ResponseEntity.ok(new IngestResponse("success", totalActivities,
                    "Activities ingested and processed for the date."));
        } catch (Exception e) {
            logger.error("Error during ingestion process", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to ingest activities");
        }
    }

    @GetMapping("/reports/daily")
    public ResponseEntity<DailyReport> getDailyReport(
            @RequestParam @NotBlank @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}", message="Date must be YYYY-MM-DD") String date) {
        logger.info("Received request for daily report for date: {}", date);
        DailyReport report = dailyReports.get(date);
        if (report == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Report not found for date: " + date);
        }
        return ResponseEntity.ok(report);
    }

    @PostMapping("/reports/publish")
    public ResponseEntity<PublishResponse> publishReport(@RequestBody @Valid PublishRequest request) {
        String date = request.getDate();
        List<String> recipients = Optional.ofNullable(request.getRecipients())
                .filter(r -> !r.isEmpty())
                .orElse(Collections.singletonList(DEFAULT_ADMIN_EMAIL));
        logger.info("Publish report request for date {} to recipients {}", date, recipients);

        DailyReport report = dailyReports.get(date);
        if (report == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Report not found for date: " + date);
        }

        CompletableFuture.runAsync(() -> sendReportEmail(report, recipients)); // fire-and-forget

        return ResponseEntity.ok(new PublishResponse("success", "Daily report sent to recipients."));
    }

    private void sendReportEmail(DailyReport report, List<String> recipients) {
        // TODO: Replace with actual email sending logic
        logger.info("Sending report email for date {} to {}", report.getDate(), recipients);
        SentEmail email = new SentEmail(report.getDate(), recipients, Instant.now());
        sentEmails.add(email);
        logger.info("Report email sent (mock) for date {}", report.getDate());
    }

    private String todayIsoDate() {
        return java.time.LocalDate.now().toString();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestRequest {
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}", message="Date must be YYYY-MM-DD")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestResponse {
        private String status;
        private int ingestedCount;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyReport {
        private String date;
        private int totalActivities;
        private List<String> frequentActivityTypes;
        private List<String> anomalies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishRequest {
        @NotBlank
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}", message="Date must be YYYY-MM-DD")
        private String date;
        private List<@NotBlank String> recipients;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishResponse {
        private String status;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class SentEmail {
        private String reportDate;
        private List<String> recipients;
        private Instant sentAt;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}