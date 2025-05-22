package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String ANALYZE_REQUEST_MODEL = "cyodaAnalyzeRequest";
    private static final String REPORT_MODEL = "cyodaReport";

    private final List<String> staticSubscribers = List.of(
            "user1@example.com",
            "user2@example.com"
    );

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class AnalyzeRequest {
        @NotBlank
        private String csvUrl;
    }

    @Data
    @AllArgsConstructor
    static class AnalyzeResponse {
        private String taskId;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportResult {
        private String taskId;
        private String status; // completed | pending | failed
        private SummaryStatistics summaryStatistics;
        private BasicTrends basicTrends;
        private boolean emailSent;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class SummaryStatistics {
        private Double meanPrice;
        private Double medianPrice;
        private Integer totalListings;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class BasicTrends {
        private String priceTrend;
    }

    @Data
    @AllArgsConstructor
    static class SubscribersResponse {
        private List<String> subscribers;
    }

    // Workflow function for cyodaAnalyzeRequest entity
    private CompletableFuture<ObjectNode> processCyodaAnalyzeRequest(ObjectNode entity) {
        logger.info("Workflow processCyodaAnalyzeRequest started for entity: {}", entity);

        if (!entity.hasNonNull("csvUrl")) {
            logger.warn("Entity missing csvUrl, skipping workflow");
            return CompletableFuture.completedFuture(entity);
        }

        String taskId = entity.hasNonNull("id") ? entity.get("id").asText() : UUID.randomUUID().toString();
        entity.put("id", taskId);
        entity.put("status", "processing");
        entity.put("requestedAt", Instant.now().toString());

        return CompletableFuture.supplyAsync(() -> {
            try {
                String csvUrl = entity.get("csvUrl").asText();
                logger.info("Downloading CSV for taskId={} from URL: {}", taskId, csvUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(csvUrl))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to download CSV: HTTP " + response.statusCode());
                }
                return response.body();
            } catch (Exception e) {
                throw new RuntimeException("CSV download failed", e);
            }
        }, executor).thenApplyAsync(csvData -> {
            logger.info("Performing mock analysis for taskId={}", taskId);
            SummaryStatistics stats = new SummaryStatistics(500000.0, 450000.0, 1000);
            BasicTrends trends = new BasicTrends("stable");
            if (stats.getMeanPrice() != null && stats.getMeanPrice() > 600000) {
                trends.setPriceTrend("increasing");
            }

            ObjectNode report = objectMapper.createObjectNode();
            report.put("taskId", taskId);
            report.put("status", "completed");
            report.set("summaryStatistics", objectMapper.valueToTree(stats));
            report.set("basicTrends", objectMapper.valueToTree(trends));
            report.put("emailSent", false);

            try {
                entityService.addItem(REPORT_MODEL, ENTITY_VERSION, report, e -> CompletableFuture.completedFuture(e));
            } catch (Exception ex) {
                logger.error("Failed to add report entity in workflow: {}", ex.getMessage(), ex);
            }

            return report;
        }, executor).thenComposeAsync(report -> {
            CompletableFuture<Void> emailFuture = CompletableFuture.runAsync(() -> {
                logger.info("Sending email report asynchronously for taskId={}", taskId);
                try {
                    Thread.sleep(1000);
                    report.put("emailSent", true);
                    entityService.updateItem(REPORT_MODEL, ENTITY_VERSION, report);
                    logger.info("Email report sent and report updated for taskId={}", taskId);
                } catch (Exception e) {
                    logger.error("Failed to send email or update report for taskId={}: {}", taskId, e.getMessage(), e);
                }
            }, executor);

            entity.put("status", "completed");
            entity.put("completedAt", Instant.now().toString());

            return emailFuture.thenApply(v -> entity);
        }, executor).exceptionally(ex -> {
            logger.error("Workflow processing failed for taskId={}: {}", taskId, ex.getMessage(), ex);
            entity.put("status", "failed");
            entity.put("errorMessage", ex.getMessage());
            entity.put("failedAt", Instant.now().toString());
            return entity;
        });
    }

    @PostMapping("/data/analyze")
    public CompletableFuture<ResponseEntity<AnalyzeResponse>> analyzeData(@RequestBody @Valid AnalyzeRequest request) {
        logger.info("Received analyze request for CSV URL: {}", request.getCsvUrl());

        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("csvUrl", request.getCsvUrl());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ANALYZE_REQUEST_MODEL,
                ENTITY_VERSION,
                entity,
                this::processCyodaAnalyzeRequest
        );

        return idFuture.thenApply(id -> {
            logger.info("Analyze request accepted with id: {}", id);
            return ResponseEntity.ok(new AnalyzeResponse(id.toString(), "started"));
        }).exceptionally(ex -> {
            logger.error("Failed to add analyze request: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add analyze request");
        });
    }

    @GetMapping("/report/{taskId}")
    public CompletableFuture<ResponseEntity<ReportResult>> getReport(@PathVariable String taskId) {
        logger.info("Fetching report for taskId: {}", taskId);
        UUID uuid;
        try {
            uuid = UUID.fromString(taskId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid taskId format");
        }
        return entityService.getItem(REPORT_MODEL, ENTITY_VERSION, uuid)
                .thenApply(optionalEntity -> {
                    if (optionalEntity.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
                    }
                    ObjectNode reportNode = optionalEntity.get();
                    try {
                        return ResponseEntity.ok(objectMapper.treeToValue(reportNode, ReportResult.class));
                    } catch (Exception e) {
                        logger.error("Failed to deserialize report entity: {}", e.getMessage(), e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse report");
                    }
                });
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        logger.info("Returning static subscribers list");
        return ResponseEntity.ok(new SubscribersResponse(staticSubscribers));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}