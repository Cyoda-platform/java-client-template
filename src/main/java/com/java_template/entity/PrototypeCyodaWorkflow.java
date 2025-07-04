package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-prototype")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String entityModel = "prototype";
    private final String publishEntityModel = "prototypePublish";

    @Resource
    private EntityService entityService;

    // Workflow function for 'prototype' entity
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processprototype = entity -> CompletableFuture.supplyAsync(() -> {
        logger.info("Workflow processprototype started for entity date: {}", entity.path("date").asText());

        JsonNode rawActivities = entity.path("rawActivities");
        int totalActivities = 0;
        Map<String, Integer> activityTypeFrequency = new HashMap<>();
        List<String> anomalies = new ArrayList<>();

        if (rawActivities.isArray()) {
            for (JsonNode activity : rawActivities) {
                totalActivities++;
                String title = activity.path("Title").asText("Unknown");
                activityTypeFrequency.merge(title, 1, Integer::sum);
            }
        } else {
            anomalies.add("No activities data found or not an array");
        }

        if (totalActivities == 0) {
            anomalies.add("No activities found for the date.");
        }
        if (activityTypeFrequency.values().stream().anyMatch(freq -> freq > 100)) {
            anomalies.add("Some activity type frequency unusually high.");
        }

        entity.remove("rawActivities");
        entity.put("totalActivities", totalActivities);
        entity.putPOJO("frequentActivityTypes", new ArrayList<>(activityTypeFrequency.keySet()));
        entity.putPOJO("anomalies", anomalies);

        logger.info("Workflow processprototype finished for date {} with totalActivities {}, anomalies {}", entity.path("date").asText(), totalActivities, anomalies);

        return entity;
    });

    // Workflow function for 'prototypePublish' entity
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processprototypePublish = entity -> CompletableFuture.supplyAsync(() -> {
        String date = entity.path("date").asText();
        JsonNode recipientsNode = entity.path("recipients");
        List<String> recipients = new ArrayList<>();
        if (recipientsNode.isArray()) {
            for (JsonNode r : recipientsNode) {
                if (r.isTextual() && !r.asText().isBlank()) {
                    recipients.add(r.asText());
                }
            }
        }
        if (recipients.isEmpty()) {
            recipients.add(DEFAULT_ADMIN_EMAIL);
        }

        logger.info("Workflow processprototypePublish started for date {} to recipients {}", date, recipients);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.date", "EQUALS", date));
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture =
                    entityService.getItemsByCondition(entityModel, ENTITY_VERSION, condition);
            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.join();

            if (items.isEmpty()) {
                logger.warn("No daily report found for date {} while publishing", date);
            } else {
                JsonNode reportNode = items.get(0);
                sendReportEmail(reportNode, recipients, date);
            }
        } catch (Exception e) {
            logger.error("Error sending report email in workflow processprototypePublish for date: " + date, e);
        }

        logger.info("Workflow processprototypePublish finished for date {}", date);

        return entity;
    });

    @PostMapping("/activities/ingest")
    public ResponseEntity<IngestResponse> ingestActivities(@RequestBody @Valid IngestRequest request) throws Exception {
        String date = Optional.ofNullable(request.getDate()).orElse(todayIsoDate());
        logger.info("Received ingestion request for date: {}", date);

        String fakerestUrl = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
        String response = restTemplate.getForObject(fakerestUrl, String.class);
        JsonNode activitiesNode = objectMapper.readTree(response);

        ObjectNode prototypeEntity = objectMapper.createObjectNode();
        prototypeEntity.put("date", date);
        prototypeEntity.set("rawActivities", activitiesNode);

        CompletableFuture<UUID> savedIdFuture = entityService.addItem(
                entityModel,
                ENTITY_VERSION,
                prototypeEntity,
                processprototype
        );

        UUID savedId = savedIdFuture.join();

        logger.info("Ingested activities for date {} with saved entity id: {}", date, savedId);
        return ResponseEntity.ok(new IngestResponse("success", date, savedId.toString()));
    }

    @GetMapping("/reports/daily")
    public ResponseEntity<DailyReport> getDailyReport(
            @RequestParam @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD") String date) throws Exception {
        logger.info("Received request for daily report for date: {}", date);

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", date));

        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture =
                entityService.getItemsByCondition(entityModel, ENTITY_VERSION, condition);

        com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.join();

        if (items.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Report not found for date: " + date);
        }

        JsonNode node = items.get(0);
        DailyReport report = objectMapper.treeToValue(node, DailyReport.class);

        return ResponseEntity.ok(report);
    }

    @PostMapping("/reports/publish")
    public ResponseEntity<PublishResponse> publishReport(@RequestBody @Valid PublishRequest request) throws Exception {
        String date = request.getDate();
        List<String> recipients = Optional.ofNullable(request.getRecipients())
                .filter(r -> !r.isEmpty())
                .orElse(Collections.singletonList(DEFAULT_ADMIN_EMAIL));
        logger.info("Publish report request for date {} to recipients {}", date, recipients);

        ObjectNode publishEntity = objectMapper.createObjectNode();
        publishEntity.put("date", date);
        publishEntity.putPOJO("recipients", recipients);

        CompletableFuture<UUID> savedIdFuture = entityService.addItem(
                publishEntityModel,
                ENTITY_VERSION,
                publishEntity,
                processprototypePublish
        );

        UUID savedId = savedIdFuture.join();

        logger.info("Publish entity persisted with id {} and email sending workflow triggered", savedId);

        return ResponseEntity.ok(new PublishResponse("success", "Daily report publish request accepted and email sending triggered."));
    }

    private void sendReportEmail(JsonNode report, List<String> recipients, String date) {
        logger.info("Sending report email for date {} to recipients {}", date, recipients);
        logger.info("Report content: {}", report.toString());
        // Simulate email sending; in production, implement actual email sending here
        logger.info("Report email sent (mock) for date {}", date);
    }

    private String todayIsoDate() {
        return java.time.LocalDate.now().toString();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestResponse {
        private String status;
        private String date;
        private String entityId;
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
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
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
}