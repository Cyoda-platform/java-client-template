package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-prototype")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private final String entityModel = "prototype";
    private final String publishEntityModel = "prototypePublish";

    @Resource
    private EntityService entityService;

    public Controller(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                prototypeEntity
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
            logger.error("Report not found for date: {}", date);
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
                publishEntity
        );

        UUID savedId = savedIdFuture.join();

        logger.info("Publish entity persisted with id {}", savedId);

        return ResponseEntity.ok(new PublishResponse("success", "Daily report publish request accepted."));
    }

    private void sendReportEmail(JsonNode report, List<String> recipients, String date) {
        logger.info("Sending report email for date {} to recipients {}", date, recipients);
        logger.info("Report content: {}", report.toString());
        logger.info("Report email sent (mock) for date {}", date);
    }

    private String todayIsoDate() {
        return java.time.LocalDate.now().toString();
    }

    // DTO classes

    public static class IngestRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;

        public IngestRequest() {}

        public IngestRequest(String date) {
            this.date = date;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    public static class IngestResponse {
        private String status;
        private String date;
        private String entityId;

        public IngestResponse() {}

        public IngestResponse(String status, String date, String entityId) {
            this.status = status;
            this.date = date;
            this.entityId = entityId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getEntityId() {
            return entityId;
        }

        public void setEntityId(String entityId) {
            this.entityId = entityId;
        }
    }

    public static class DailyReport {
        private String date;
        private int totalActivities;
        private List<String> frequentActivityTypes;
        private List<String> anomalies;

        public DailyReport() {}

        public DailyReport(String date, int totalActivities, List<String> frequentActivityTypes, List<String> anomalies) {
            this.date = date;
            this.totalActivities = totalActivities;
            this.frequentActivityTypes = frequentActivityTypes;
            this.anomalies = anomalies;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public int getTotalActivities() {
            return totalActivities;
        }

        public void setTotalActivities(int totalActivities) {
            this.totalActivities = totalActivities;
        }

        public List<String> getFrequentActivityTypes() {
            return frequentActivityTypes;
        }

        public void setFrequentActivityTypes(List<String> frequentActivityTypes) {
            this.frequentActivityTypes = frequentActivityTypes;
        }

        public List<String> getAnomalies() {
            return anomalies;
        }

        public void setAnomalies(List<String> anomalies) {
            this.anomalies = anomalies;
        }
    }

    public static class PublishRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be YYYY-MM-DD")
        private String date;
        private List<@NotBlank String> recipients;

        public PublishRequest() {}

        public PublishRequest(String date, List<String> recipients) {
            this.date = date;
            this.recipients = recipients;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = recipients;
        }
    }

    public static class PublishResponse {
        private String status;
        private String message;

        public PublishResponse() {}

        public PublishResponse(String status, String message) {
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
}