package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String FAKERES_API_ACTIVITIES_URL = "https://fakerestapi.azurewebsites.net/api/v1/Activities";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/activities/fetch")
    public ResponseEntity<FetchResponse> fetchAndProcessActivities(@RequestBody @Valid FetchRequest request) {
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        logger.info("Received fetch request for date {} with jobId {}", request.getDate(), jobId);
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));

        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting data ingestion for date {}", request.getDate());
                URI uri = URI.create(FAKERES_API_ACTIVITIES_URL);
                String jsonResponse = restTemplate.getForObject(uri, String.class);
                JsonNode activitiesNode = objectMapper.readTree(jsonResponse);

                Map<Integer, UserActivitySummary> userSummaries = new HashMap<>();
                if (activitiesNode.isArray()) {
                    for (JsonNode activity : activitiesNode) {
                        int userId = activity.path("userId").asInt(-1);
                        String activityName = activity.path("name").asText("unknown");
                        if (userId < 0) {
                            logger.warn("Skipping activity without userId: {}", activity);
                            continue;
                        }
                        UserActivitySummary summary = userSummaries.computeIfAbsent(userId, k -> new UserActivitySummary());
                        summary.totalActivities++;
                        summary.activityTypes.merge(activityName.toLowerCase(), 1, Integer::sum);
                    }
                } else {
                    logger.warn("Unexpected response format: expected array");
                }

                userSummaries.forEach((userId, summary) -> {
                    if (summary.totalActivities > 10) {
                        summary.anomalies.add("Unusually high activity count");
                    }
                });

                // Prepare entities to save
                List<DailyReportEntity> reportsToSave = new ArrayList<>();
                userSummaries.forEach((uid, summ) -> reportsToSave.add(new DailyReportEntity(request.getDate(), uid, summ)));

                // Use entityService to add items in batch
                entityService.addItems("DailyReportEntity", ENTITY_VERSION, reportsToSave);

                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Completed processing for date {}", request.getDate());
            } catch (Exception e) {
                logger.error("Error processing date {}: {}", request.getDate(), e.getMessage(), e);
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
            }
        });

        return ResponseEntity.ok(new FetchResponse("success",
                "Activities fetched, processed, and report generation started for " + request.getDate()));
    }

    @GetMapping("/reports/daily/{date}")
    public ResponseEntity<DailyReportResponse> getDailyReport(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Received request for report date {}", date);

        // Create search condition for date equals requested date
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("DailyReportEntity", ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.join();

        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date: " + date);
        }

        List<UserReport> userReports = new ArrayList<>();
        for (JsonNode node : items) {
            int userId = node.path("userId").asInt();
            UserActivitySummary summary = null;
            try {
                summary = objectMapper.treeToValue(node.path("activitySummary"), UserActivitySummary.class);
            } catch (Exception e) {
                logger.error("Error deserializing UserActivitySummary: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing report data");
            }
            userReports.add(new UserReport(userId, summary));
        }

        DailyReportResponse response = new DailyReportResponse(date, userReports);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reports/send")
    public ResponseEntity<SendReportResponse> sendReport(@RequestBody @Valid SendReportRequest request) {
        logger.info("Received send request for date {} to {}", request.getDate(), request.getAdminEmails());

        // Check if report exists by querying entityService
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", request.getDate()));

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("DailyReportEntity", ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.join();
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date: " + request.getDate());
        }

        // Simulate sending email
        logger.info("Simulating email send for date {} to {}", request.getDate(), request.getAdminEmails());
        return ResponseEntity.ok(new SendReportResponse("success",
                "Report for " + request.getDate() + " sent to " + String.join(", ", request.getAdminEmails())));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: {} - {}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FetchRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
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
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
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
    private static class DailyReportResponse {
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DailyReportEntity {
        private String date;
        private int userId;
        private UserActivitySummary activitySummary;
    }
}