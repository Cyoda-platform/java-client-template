package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("/prototype/jobs")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String EXTERNAL_API_ACTIVITIES_URL = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
    private static final String ENTITY_NAME_JOB = "Job";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PostMapping("/activities/ingest")
    public IngestResponse ingestActivities(@RequestBody @Valid IngestRequest request) {
        String date = request.getDate() != null ? request.getDate() : Instant.now().toString().substring(0, 10);
        logger.info("Received ingest request for date {}", date);
        UUID jobId = UUID.randomUUID();
        logger.info("Starting ingestion job with id {}", jobId);
        CompletableFuture.runAsync(() -> processIngestionJob(jobId, date));
        return new IngestResponse("success", "Data ingestion, processing, and report generation started", jobId.toString());
    }

    private void processIngestionJob(UUID jobId, String date) {
        try {
            logger.info("[Job {}] Fetching activities from external API", jobId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(EXTERNAL_API_ACTIVITIES_URL))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("[Job {}] Failed to fetch data. Status code: {}", jobId, response.statusCode());
                return;
            }
            JsonNode rootNode = objectMapper.readTree(response.body());
            logger.info("[Job {}] Fetched {} activities", jobId, rootNode.size());

            // Build Job entity data with ingestion info
            Job job = new Job();
            job.setId(jobId);
            job.setDate(date);
            job.setStatus("IN_PROGRESS");
            job.setActivityCount(rootNode.size());

            // Add Job entity via EntityService
            entityService.addItem(ENTITY_NAME_JOB, ENTITY_VERSION, job).join();

            ActivityReport report = analyzeActivities(rootNode, date);
            // Save report as JobReport entity or as part of Job extended data - here we keep it local due to minor logic

            // Update Job status to COMPLETED
            job.setStatus("COMPLETED");
            entityService.updateItem(ENTITY_NAME_JOB, ENTITY_VERSION, jobId, job).join();

            logger.info("[Job {}] Sending daily report email to admin (mocked)", jobId);
        } catch (Exception e) {
            logger.error("[Job {}] Error during ingestion: {}", jobId, e.getMessage(), e);
        }
    }

    private ActivityReport analyzeActivities(JsonNode activitiesNode, String date) {
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        for (JsonNode node : activitiesNode) {
            total++;
            String name = node.path("name").asText("unknown");
            counts.put(name, counts.getOrDefault(name, 0) + 1);
        }
        String trends = "No complex trends calculated in prototype.";
        List<String> anomalies = Collections.emptyList();
        logger.info("Processed report for date {}: total={}, types={}", date, total, counts.size());
        return new ActivityReport(date, total, counts, trends, anomalies);
    }

    @GetMapping("/reports/daily")
    public ActivityReport getDailyReport(@RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date) {
        logger.info("Request for daily report of date {}", date);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME_JOB, ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.join();
        if (items.isEmpty()) {
            logger.error("No report found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }
        // Convert first Job entity to ActivityReport (mocked here)
        ObjectNode jobNode = (ObjectNode) items.get(0);
        return new ActivityReport(
                jobNode.path("date").asText(),
                jobNode.path("activityCount").asInt(),
                Collections.emptyMap(),
                "No complex trends calculated in prototype.",
                Collections.emptyList()
        );
    }

    @GetMapping("/reports/{reportId}")
    public ActivityReport getReportById(@PathVariable @NotBlank String reportId) {
        logger.info("Request for report with id {}", reportId);
        UUID uuid;
        try {
            uuid = UUID.fromString(reportId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid report id format: {}", reportId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report id format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME_JOB, ENTITY_VERSION, uuid);
        ObjectNode jobNode = itemFuture.join();
        if (jobNode == null || jobNode.isEmpty()) {
            logger.error("Report with id {} not found", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for id " + reportId);
        }
        return new ActivityReport(
                jobNode.path("date").asText(),
                jobNode.path("activityCount").asInt(),
                Collections.emptyMap(),
                "No complex trends calculated in prototype.",
                Collections.emptyList()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    @Data
    public static class IngestRequest {
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
        private String date;
    }

    @Data
    public static class IngestResponse {
        private final String status;
        private final String message;
        private final String reportId;
    }

    @Data
    public static class ActivityReport {
        private final String date;
        private final int totalActivities;
        private final Map<String, Integer> activityTypes;
        private final String trends;
        private final List<String> anomalies;
    }

    @Data
    public static class Job {
        private UUID id;
        private String date;
        private String status;
        private int activityCount;
    }
}