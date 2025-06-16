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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-activities")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String ENTITY_NAME = "ActivityReport";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    // Endpoint triggers ingestion by adding minimal entity with workflow processing all logic asynchronously before persistence
    @PostMapping("/ingest")
    public ResponseEntity<GenericResponse> ingestActivities(@RequestBody @Valid IngestRequest ingestRequest) {
        String dateStr = ingestRequest.getDate() != null ? ingestRequest.getDate() : LocalDate.now().toString();
        logger.info("Received ingestion request for date {}", dateStr);

        ObjectNode initialEntity = objectMapper.createObjectNode();
        initialEntity.put("date", dateStr);

        // Add item with workflow function - all processing happens asynchronously inside workflow
        CompletableFuture<UUID> addFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                initialEntity,
                this::processActivityReport
        );

        // Return immediately
        return ResponseEntity.ok(new GenericResponse("success", "Data ingestion and processing started for date " + dateStr));
    }

    // Retrieve ActivityReport by date
    @GetMapping("/report")
    public ResponseEntity<ActivityReport> getReport(
            @RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date) {
        logger.info("Fetching report for date {}", date);

        String condition = String.format("{\"date\":\"%s\"}", date);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);

        ArrayNode resultArray = filteredItemsFuture.join();
        if (resultArray == null || resultArray.isEmpty()) {
            logger.warn("Report not found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }

        ObjectNode objNode = (ObjectNode) resultArray.get(0);
        try {
            ActivityReport report = objectMapper.treeToValue(objNode, ActivityReport.class);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to parse ActivityReport object: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse report data");
        }
    }

    // Endpoint to request sending report email; actual email sending is done asynchronously via secondary entity created in workflow
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

        // Create EmailJob entity asynchronously to trigger email sending workflow later
        ObjectNode emailJobEntity = objectMapper.createObjectNode();
        emailJobEntity.put("emailTo", adminEmail);
        emailJobEntity.put("subject", "Requested Activity Report for " + date);
        emailJobEntity.put("body", "The activity report for date " + date + " has been requested.");
        emailJobEntity.put("createdAt", OffsetDateTime.now().toString());
        emailJobEntity.put("status", "pending");
        emailJobEntity.put("reportDate", date);

        // Add EmailJob entity with no workflow (identity function)
        entityService.addItem("EmailJob", "1.0", emailJobEntity, Function.identity());

        return ResponseEntity.ok(new GenericResponse("success", "Report send request received for " + date + " to " + adminEmail));
    }

    /**
     * Workflow function processing ActivityReport entity before persistence.
     * This function:
     *  - Fetches external data,
     *  - Computes and enriches the entity,
     *  - Creates secondary entities as needed,
     *  - Returns modified entity for persistence.
     *  
     *  Must not add/update/delete entity of the same model (ActivityReport) to avoid recursion.
     */
    private CompletableFuture<ObjectNode> processActivityReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateStr = entity.path("date").asText(null);
                if (dateStr == null || dateStr.isBlank()) {
                    throw new IllegalArgumentException("Entity 'date' field is missing or empty");
                }
                logger.info("Workflow: processing ActivityReport for date {}", dateStr);

                // Fetch external activities from Fakerest API with RestTemplate (sync call inside async block)
                URI fakerestUri = new URI("https://fakerestapi.azurewebsites.net/api/v1/Activities");
                String rawJson = restTemplate.getForObject(fakerestUri, String.class);
                if (rawJson == null) {
                    throw new IllegalStateException("Failed to fetch data from Fakerest API");
                }
                JsonNode activitiesNode = objectMapper.readTree(rawJson);

                int totalActivities = 0;
                Map<String, Integer> activityTypesCount = new HashMap<>();
                activityTypesCount.put("typeA", 0);
                activityTypesCount.put("typeB", 0);
                activityTypesCount.put("typeC", 0);

                if (activitiesNode.isArray()) {
                    totalActivities = activitiesNode.size();
                    for (JsonNode activityNode : activitiesNode) {
                        String activityName = activityNode.path("activityName").asText("");
                        int mod = activityName.length() % 3;
                        switch (mod) {
                            case 0 -> activityTypesCount.merge("typeA", 1, Integer::sum);
                            case 1 -> activityTypesCount.merge("typeB", 1, Integer::sum);
                            default -> activityTypesCount.merge("typeC", 1, Integer::sum);
                        }
                    }
                }

                // Modify entity directly - these changes will be persisted
                entity.put("totalActivities", totalActivities);
                entity.set("activityTypes", objectMapper.valueToTree(activityTypesCount));
                entity.set("trends", objectMapper.valueToTree(Map.of("mostActiveUser", "user123", "peakActivityHour", "15:00")));
                entity.set("anomalies", objectMapper.valueToTree(new String[]{"User456 showed unusually high activity"}));

                logger.info("Workflow: enriched ActivityReport entity with computed values");

                // Optional: create secondary log entity for audit or monitoring
                ObjectNode auditLogEntity = objectMapper.createObjectNode();
                auditLogEntity.put("entityModel", ENTITY_NAME);
                auditLogEntity.put("entityDate", dateStr);
                auditLogEntity.put("event", "ActivityReport processed");
                auditLogEntity.put("timestamp", OffsetDateTime.now().toString());

                entityService.addItem("AuditLog", "1.0", auditLogEntity, Function.identity());

                logger.info("Workflow: created AuditLog entity");

                // Return modified entity for persistence
                return entity;

            } catch (Exception e) {
                logger.error("Error during processActivityReport workflow: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }

    // DTOs

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

}