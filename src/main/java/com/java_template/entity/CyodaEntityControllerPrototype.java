package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-activities")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String ENTITY_NAME = "ActivityReport";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @PostMapping("/ingest")
    public ResponseEntity<GenericResponse> ingestActivities(@RequestBody @Valid IngestRequest ingestRequest) {
        String dateStr = ingestRequest.getDate() != null ? ingestRequest.getDate() : LocalDate.now().toString();
        logger.info("Received ingestion request for date {}", dateStr);
        String jobId = "ingest-" + dateStr + "-" + OffsetDateTime.now().toEpochSecond();
        entityJobs.put(jobId, new JobStatus("processing", OffsetDateTime.now()));

        CompletableFuture.runAsync(() -> {
            try {
                ingestAndProcessData(dateStr);
                entityJobs.put(jobId, new JobStatus("completed", OffsetDateTime.now()));
                logger.info("Ingestion and processing completed for date {}", dateStr);
            } catch (Exception e) {
                entityJobs.put(jobId, new JobStatus("failed", OffsetDateTime.now()));
                logger.error("Error during ingestion for date {}: {}", dateStr, e.getMessage(), e);
            }
        });

        return ResponseEntity.ok(new GenericResponse("success", "Data ingestion and processing started for date " + dateStr));
    }

    @GetMapping("/report")
    public ResponseEntity<ActivityReport> getReport(
            @RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date) {
        logger.info("Fetching report for date {}", date);

        // Use entityService.getItemsByCondition to find report by date
        String condition = String.format("{\"date\":\"%s\"}", date);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);

        ArrayNode resultArray = filteredItemsFuture.join();
        if (resultArray == null || resultArray.isEmpty()) {
            logger.warn("Report not found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }

        // Extract first matching ObjectNode and convert to ActivityReport
        ObjectNode objNode = (ObjectNode) resultArray.get(0);
        try {
            ActivityReport report = objectMapper.treeToValue(objNode, ActivityReport.class);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to parse ActivityReport object: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse report data");
        }
    }

    @PostMapping("/report/send")
    public ResponseEntity<GenericResponse> sendReportEmail(@RequestBody @Valid SendReportRequest request) {
        String date = request.getDate();
        String adminEmail = request.getAdminEmail();
        logger.info("Request to send report for date {} to admin {}", date, adminEmail);

        String condition = String.format("{\"date\":\"%s\"}", date);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);

        ArrayNode resultArray = filteredItemsFuture.join();
        if (resultArray == null || resultArray.isEmpty()) {
            logger.warn("Report not found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }

        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Sending report email to {}", adminEmail);
                Thread.sleep(1000);
                logger.info("Report email sent to {}", adminEmail);
            } catch (InterruptedException e) {
                logger.error("Error sending report email: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        });

        return ResponseEntity.ok(new GenericResponse("success", "Report for " + date + " sent to " + adminEmail));
    }

    private void ingestAndProcessData(String dateStr) {
        logger.info("Fetching activity data from Fakerest API...");
        try {
            URI uri = new URI("https://fakerestapi.azurewebsites.net/api/v1/Activities");
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

            // Use entityService.addItem to store the report (replace local cache)
            entityService.addItem(ENTITY_NAME, ENTITY_VERSION, report).join();

            logger.info("Processed and stored report for date {}", dateStr);
        } catch (Exception e) {
            logger.error("Failed to ingest and process data: {}", e.getMessage(), e);
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

}