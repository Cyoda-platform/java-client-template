package com.java_template.entity;

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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    private final Map<String, JobStatus> entityJobs = new HashMap<>();

    private static final String FAKERES_API_ACTIVITIES_URL = "https://fakerestapi.azurewebsites.net/api/v1/Activities";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function for DailyReportEntity.
     * Enriches entity with anomalies, supplementary data asynchronously.
     * @param entityNode ObjectNode representing DailyReportEntity
     * @return CompletableFuture<ObjectNode> with mutated entity
     */
    private CompletableFuture<ObjectNode> processDailyReportEntity(ObjectNode entityNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String date = entityNode.path("date").asText(null);
                int userId = entityNode.path("userId").asInt(-1);
                ObjectNode activitySummary = (ObjectNode) entityNode.get("activitySummary");
                if (activitySummary == null) {
                    activitySummary = objectMapper.createObjectNode();
                    entityNode.set("activitySummary", activitySummary);
                }
                int totalActivities = activitySummary.path("totalActivities").asInt(0);
                ObjectNode activityTypesNode = activitySummary.with("activityTypes");
                ArrayNode anomaliesNode = (ArrayNode) activitySummary.get("anomalies");
                if (anomaliesNode == null) {
                    anomaliesNode = objectMapper.createArrayNode();
                    activitySummary.set("anomalies", anomaliesNode);
                }
                // Add anomaly if totalActivities is zero
                if (totalActivities == 0) {
                    if (!containsString(anomaliesNode, "No activities recorded")) {
                        anomaliesNode.add("No activities recorded");
                    }
                }
                // Add anomaly if unusually high activities
                if (totalActivities > 10) {
                    if (!containsString(anomaliesNode, "Unusually high activity count")) {
                        anomaliesNode.add("Unusually high activity count");
                    }
                }
                // Fetch supplementary UserProfileEntity data and add userName field if available
                if (userId >= 0) {
                    SearchConditionRequest cond = SearchConditionRequest.group("AND",
                            Condition.of("$.userId", "EQUALS", userId));
                    CompletableFuture<ArrayNode> userProfileFuture =
                            entityService.getItemsByCondition("UserProfileEntity", ENTITY_VERSION, cond);
                    ArrayNode profiles = userProfileFuture.join();
                    if (profiles != null && !profiles.isEmpty()) {
                        JsonNode userProfile = profiles.get(0);
                        String userName = userProfile.path("name").asText(null);
                        if (userName != null) {
                            entityNode.put("userName", userName);
                        }
                    }
                }
                return entityNode;
            } catch (Exception ex) {
                logger.error("Error in processDailyReportEntity workflow: {}", ex.getMessage(), ex);
                return entityNode;
            }
        });
    }

    private boolean containsString(ArrayNode arrayNode, String value) {
        for (JsonNode node : arrayNode) {
            if (node.asText("").equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Workflow function for SendReportEntity.
     * Simulates sending email asynchronously before persisting entity.
     * Since sending email is side-effect, this is fire-and-forget style.
     * @param entityNode ObjectNode representing SendReportEntity
     * @return CompletableFuture<ObjectNode> with unchanged entityNode
     */
    private CompletableFuture<ObjectNode> processSendReportEntity(ObjectNode entityNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String date = entityNode.path("date").asText(null);
                JsonNode adminsNode = entityNode.get("adminEmails");
                List<String> adminEmails = new ArrayList<>();
                if (adminsNode != null && adminsNode.isArray()) {
                    for (JsonNode emailNode : adminsNode) {
                        adminEmails.add(emailNode.asText());
                    }
                }
                // Simulate sending email - can replace with real email logic here
                logger.info("Sending report email for date {} to admins: {}", date, String.join(", ", adminEmails));
                // No change to entityNode required, just side-effect
            } catch (Exception e) {
                logger.error("Error sending report email in workflow: {}", e.getMessage(), e);
            }
            return entityNode;
        });
    }

    /**
     * POST /activities/fetch
     * Fetches activities from external API, aggregates per user,
     * creates DailyReportEntity objects, calls addItems with workflow function.
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
                // Add items with workflow for enrichment and supplementary data fetch
                entityService.addItems("DailyReportEntity", ENTITY_VERSION, reportNodes, this::processDailyReportEntity);
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
     * Persists SendReportEntity with workflow to simulate sending email asynchronously.
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
        // Build ObjectNode for SendReportEntity
        ObjectNode sendReportNode = objectMapper.createObjectNode();
        sendReportNode.put("date", request.getDate());
        ArrayNode adminEmailsNode = objectMapper.createArrayNode();
        request.getAdminEmails().forEach(adminEmailsNode::add);
        sendReportNode.set("adminEmails", adminEmailsNode);
        // Add item with workflow that sends email asynchronously
        CompletableFuture<UUID> idFuture = entityService.addItem("SendReportEntity", ENTITY_VERSION, sendReportNode, this::processSendReportEntity);
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
}