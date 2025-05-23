package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("/api/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String EXTERNAL_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
    private static final String ENTITY_NAME = "activity";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @PostMapping(value = "/activities/ingest", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public IngestResponse ingestActivities(@RequestBody @Valid IngestRequest request) {
        String dateStr = (request == null || !StringUtils.hasText(request.getDate()))
                ? LocalDate.now().toString() : request.getDate();
        logger.info("Received ingestion request for date {}", dateStr);

        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("summaryDate", dateStr);

        CompletableFuture<UUID> addFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entity, this::processactivity);

        addFuture.whenComplete((uuid, ex) -> {
            if (ex != null) {
                logger.error("Failed to ingest activities for date {}: {}", dateStr, ex.getMessage(), ex);
            } else {
                logger.info("Successfully ingested activities for date {} with id {}", dateStr, uuid);
            }
        });

        return new IngestResponse("success", "Activity data ingestion started for date " + dateStr);
    }

    @GetMapping(value = "/reports/daily", produces = MediaType.APPLICATION_JSON_VALUE)
    public DailyReportResponse getDailyReport(
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
                    String date) throws Exception {
        String dateStr = (date == null || date.isBlank()) ? LocalDate.now().toString() : date;
        logger.info("Fetching daily report for date {}", dateStr);
        String condition = String.format("summaryDate == '%s'", dateStr);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode items = filteredItemsFuture.get();
        if (items == null || items.isEmpty()) {
            logger.warn("No report found for date {}", dateStr);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + dateStr);
        }
        ObjectNode reportNode = (ObjectNode) items.get(0);
        ActivityPatternSummary summary = objectMapper.treeToValue(reportNode.get("summary"), ActivityPatternSummary.class);
        return new DailyReportResponse(dateStr, summary);
    }

    @PostMapping(value = "/reports/send", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SendReportResponse sendReport(@RequestBody @Valid SendReportRequest request) throws Exception {
        String dateStr = (request == null || !StringUtils.hasText(request.getDate()))
                ? LocalDate.now().toString() : request.getDate();
        logger.info("Sending daily report email for date {}", dateStr);

        String condition = String.format("summaryDate == '%s'", dateStr);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode items = filteredItemsFuture.get();
        if (items == null || items.isEmpty()) {
            logger.warn("No report found for date {}", dateStr);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + dateStr);
        }

        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Replace with actual email sending logic
                logger.info("Simulated sending email to admin with report for date {}", dateStr);
            } catch (Exception e) {
                logger.error("Failed to send report email for date {}: {}", dateStr, e.getMessage(), e);
            }
        });

        return new SendReportResponse("success", "Daily report sent to admin email for date " + dateStr);
    }

    private CompletableFuture<JsonNode> processactivity(JsonNode entityNode) {
        ObjectNode entity = (ObjectNode) entityNode;

        String dateStr = entity.has("summaryDate") ? entity.get("summaryDate").asText() : LocalDate.now().toString();
        logger.info("Workflow processactivity started for date {}", dateStr);

        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = URI.create(EXTERNAL_API_URL);
                String rawResponse = restTemplate.getForObject(uri, String.class);
                if (rawResponse == null) throw new RuntimeException("Received null response from external API");
                JsonNode rootNode = objectMapper.readTree(rawResponse);

                Map<Integer, Integer> userActivityCount = new HashMap<>();
                Map<String, Integer> activityTypeCount = new HashMap<>();

                if (rootNode.isArray()) {
                    for (JsonNode activityNode : rootNode) {
                        int userId = activityNode.path("id").asInt() % 10 + 1;
                        userActivityCount.put(userId, userActivityCount.getOrDefault(userId, 0) + 1);
                        String activityType = activityNode.path("title").asText("Unknown");
                        activityTypeCount.put(activityType, activityTypeCount.getOrDefault(activityType, 0) + 1);
                    }
                } else {
                    throw new RuntimeException("Unexpected JSON format from external API");
                }

                int totalUsers = userActivityCount.size();
                int totalActivities = userActivityCount.values().stream().mapToInt(Integer::intValue).sum();
                String mostFrequentActivity = activityTypeCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("N/A");
                double averageActivityPerUser = totalUsers > 0 ? ((double) totalActivities) / totalUsers : 0.0;

                List<String> anomalies = new ArrayList<>();
                for (int uid = 1; uid <= 10; uid++) {
                    if (!userActivityCount.containsKey(uid)) {
                        anomalies.add("User " + uid + " had zero activities");
                    }
                }
                anomalies.add("Spike in 'Running' activity at 15:00"); // TODO: replace with real anomaly logic

                ActivityPatternSummary summary = new ActivityPatternSummary(
                        totalUsers,
                        totalActivities,
                        mostFrequentActivity,
                        averageActivityPerUser,
                        anomalies
                );

                ObjectNode summaryNode = objectMapper.valueToTree(summary);
                entity.set("summary", summaryNode);

                entity.put("summaryDate", dateStr);

                logger.info("Workflow processactivity completed for date {}", dateStr);
                return entity;
            } catch (Exception e) {
                logger.error("Error in workflow processactivity: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendReportRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendReportResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyReportResponse {
        private String date;
        private ActivityPatternSummary summary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityPatternSummary {
        private int totalUsers;
        private int totalActivities;
        private String mostFrequentActivity;
        private double averageActivityPerUser;
        private List<String> anomalies;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Internal error: {}", ex.getMessage(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("status", 500);
        error.put("error", "Internal server error");
        return error;
    }
}