package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-prototype")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    private final Map<String, JobStatus> entityJobs = new HashMap<>();

    private static final String FAKERES_API_ACTIVITIES_URL = "https://fakerestapi.azurewebsites.net/api/v1/Activities";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /activities/fetch
     * Fetches activities from external API, aggregates per user,
     * creates DailyReportEntity objects, calls addItems without workflow function.
     */
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
                        summary.activityTypes.merge(activityName.toLowerCase(Locale.ROOT), 1, Integer::sum);
                    }
                } else {
                    logger.warn("Unexpected response format: expected array");
                }
                List<ObjectNode> reportNodes = new ArrayList<>();
                for (Map.Entry<Integer, UserActivitySummary> entry : userSummaries.entrySet()) {
                    ObjectNode reportNode = objectMapper.createObjectNode();
                    reportNode.put("date", request.getDate());
                    reportNode.put("userId", entry.getKey());
                    ObjectNode activitySummaryNode = objectMapper.createObjectNode();
                    activitySummaryNode.put("totalActivities", entry.getValue().totalActivities);
                    ObjectNode activityTypesNode = objectMapper.createObjectNode();
                    entry.getValue().activityTypes.forEach(activityTypesNode::put);
                    activitySummaryNode.set("activityTypes", activityTypesNode);
                    ArrayNode anomaliesArray = objectMapper.createArrayNode();
                    entry.getValue().anomalies.forEach(anomaliesArray::add);
                    activitySummaryNode.set("anomalies", anomaliesArray);
                    reportNode.set("activitySummary", activitySummaryNode);
                    reportNodes.add(reportNode);
                }
                // Add items without workflow
                entityService.addItems("DailyReportEntity", ENTITY_VERSION, reportNodes);
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

    /**
     * GET /reports/daily/{date}
     * Reads DailyReportEntity for given date.
     */
    @GetMapping("/reports/daily/{date}")
    public ResponseEntity<DailyReportResponse> getDailyReport(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date) {
        logger.info("Received request for report date {}", date);
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
            UserActivitySummary summary;
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

    /**
     * POST /reports/send
     * Persists SendReportEntity without workflow.
     */
    @PostMapping("/reports/send")
    public ResponseEntity<SendReportResponse> sendReport(@RequestBody @Valid SendReportRequest request) {
        logger.info("Received send request for date {} to {}", request.getDate(), request.getAdminEmails());
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", request.getDate()));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("DailyReportEntity", ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.join();
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date: " + request.getDate());
        }
        ObjectNode sendReportNode = objectMapper.createObjectNode();
        sendReportNode.put("date", request.getDate());
        ArrayNode adminEmailsNode = objectMapper.createArrayNode();
        request.getAdminEmails().forEach(adminEmailsNode::add);
        sendReportNode.set("adminEmails", adminEmailsNode);
        // Add item without workflow
        CompletableFuture<UUID> idFuture = entityService.addItem("SendReportEntity", ENTITY_VERSION, sendReportNode);
        UUID id = idFuture.join();
        logger.info("SendReportEntity persisted with id {}", id);
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

    // DTOs and helper classes

    // Fetch request DTO
    public static class FetchRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;

        public FetchRequest() {}

        public FetchRequest(String date) {
            this.date = date;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    // Fetch response DTO
    public static class FetchResponse {
        private String status;
        private String message;

        public FetchResponse() {}

        public FetchResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // Send report request DTO
    public static class SendReportRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;

        @NotEmpty
        private List<@Email String> adminEmails;

        public SendReportRequest() {}

        public SendReportRequest(String date, List<String> adminEmails) {
            this.date = date;
            this.adminEmails = adminEmails;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public List<String> getAdminEmails() {
            return adminEmails;
        }

        public void setAdminEmails(List<String> adminEmails) {
            this.adminEmails = adminEmails;
        }
    }

    // Send report response DTO
    public static class SendReportResponse {
        private String status;
        private String message;

        public SendReportResponse() {}

        public SendReportResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // Error response DTO
    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse() {}

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // Job status class
    public static class JobStatus {
        private String status;
        private Instant timestamp;

        public JobStatus() {}

        public JobStatus(String status, Instant timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }
    }

    // Daily report response DTO
    public static class DailyReportResponse {
        private String date;
        private List<UserReport> reports;

        public DailyReportResponse() {}

        public DailyReportResponse(String date, List<UserReport> reports) {
            this.date = date;
            this.reports = reports;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public List<UserReport> getReports() {
            return reports;
        }

        public void setReports(List<UserReport> reports) {
            this.reports = reports;
        }
    }

    // User activity summary class
    public static class UserActivitySummary {
        private int totalActivities = 0;
        private Map<String, Integer> activityTypes = new HashMap<>();
        private List<String> anomalies = new ArrayList<>();

        public UserActivitySummary() {}

        public int getTotalActivities() {
            return totalActivities;
        }

        public void setTotalActivities(int totalActivities) {
            this.totalActivities = totalActivities;
        }

        public Map<String, Integer> getActivityTypes() {
            return activityTypes;
        }

        public void setActivityTypes(Map<String, Integer> activityTypes) {
            this.activityTypes = activityTypes;
        }

        public List<String> getAnomalies() {
            return anomalies;
        }

        public void setAnomalies(List<String> anomalies) {
            this.anomalies = anomalies;
        }
    }

    // User report class
    public static class UserReport {
        private int userId;
        private UserActivitySummary activitySummary;

        public UserReport() {}

        public UserReport(int userId, UserActivitySummary activitySummary) {
            this.userId = userId;
            this.activitySummary = activitySummary;
        }

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public UserActivitySummary getActivitySummary() {
            return activitySummary;
        }

        public void setActivitySummary(UserActivitySummary activitySummary) {
            this.activitySummary = activitySummary;
        }
    }
}