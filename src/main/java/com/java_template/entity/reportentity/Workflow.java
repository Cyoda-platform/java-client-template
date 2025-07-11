package com.java_template.entity.reportentity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("reportentity")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        logger.info("Workflow for reportentity initialized, version {}", ENTITY_VERSION);
    }

    public CompletableFuture<ObjectNode> isFinalized(ObjectNode entity) {
        String status = entity.path("status").asText("");
        boolean finalized = "completed".equals(status) || "failed".equals(status);
        logger.info("[isFinalized] status='{}' finalized={}", status, finalized);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isNotFinalized(ObjectNode entity) {
        String status = entity.path("status").asText("");
        boolean notFinalized = !"completed".equals(status) && !"failed".equals(status);
        logger.info("[isNotFinalized] status='{}' notFinalized={}", status, notFinalized);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> markProcessingStarted(ObjectNode entity) {
        String status = entity.path("status").asText("");
        if (!"processing".equals(status)) {
            entity.put("status", "processing");
            entity.put("requestedAt", Instant.now().toString());
            logger.info("[markProcessingStarted] Marked entity as processing");
        } else {
            logger.info("[markProcessingStarted] Entity already processing");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> hasValidRequiredFields(ObjectNode entity) {
        String dataUrl = entity.path("dataUrl").asText(null);
        String reportType = entity.path("reportType").asText(null);
        boolean valid = dataUrl != null && !dataUrl.isBlank() && reportType != null && !reportType.isBlank();
        logger.info("[hasValidRequiredFields] dataUrl='{}' reportType='{}' valid={}", dataUrl, reportType, valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> lacksValidRequiredFields(ObjectNode entity) {
        String dataUrl = entity.path("dataUrl").asText(null);
        String reportType = entity.path("reportType").asText(null);
        boolean lacks = dataUrl == null || dataUrl.isBlank() || reportType == null || reportType.isBlank();
        logger.info("[lacksValidRequiredFields] dataUrl='{}' reportType='{}' lacks={}", dataUrl, reportType, lacks);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> markFailedMissingFields(ObjectNode entity) {
        entity.put("status", "failed");
        entity.put("statusMessage", "Missing dataUrl or reportType");
        entity.put("requestedAt", Instant.now().toString());
        logger.error("[markFailedMissingFields] Marked entity as failed due to missing required fields");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> downloadAnalyzeAndComplete(ObjectNode entity) {
        String dataUrl = entity.path("dataUrl").asText(null);
        String reportType = entity.path("reportType").asText(null);

        ArrayNode subscribersNode = (ArrayNode) entity.path("subscribers");
        List<String> subscribers = new ArrayList<>();
        if (subscribersNode != null) {
            subscribersNode.forEach(n -> {
                if (n != null && !n.isNull() && n.isTextual()) {
                    subscribers.add(n.asText());
                }
            });
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("[downloadAnalyzeAndComplete] Downloading CSV from {}", dataUrl);
                String csvData = restTemplate.getForObject(URI.create(dataUrl), String.class);
                logger.info("[downloadAnalyzeAndComplete] CSV downloaded, length={}", csvData != null ? csvData.length() : 0);
                return csvData == null ? "" : csvData;
            } catch (Exception e) {
                throw new RuntimeException("Failed to download CSV: " + e.getMessage(), e);
            }
        }).thenApplyAsync(csvData -> {
            Map<String, Object> analysisResult = analyzeCsvData(csvData, reportType);
            entity.put("status", "completed");
            entity.put("requestedAt", Instant.now().toString());
            entity.set("reportSummary", objectMapper.valueToTree(analysisResult));
            logger.info("[downloadAnalyzeAndComplete] Analysis complete, entity updated");
            return entity;
        }).thenApplyAsync(e -> {
            CompletableFuture.runAsync(() -> sendReportEmail(e.path("id").asText(null), subscribers, e.path("reportSummary")));
            return e;
        }).exceptionally(ex -> {
            logger.error("[downloadAnalyzeAndComplete] Error processing report: {}", ex.getMessage(), ex);
            entity.put("status", "failed");
            entity.put("statusMessage", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            entity.put("requestedAt", Instant.now().toString());
            return entity;
        });
    }

    private Map<String, Object> analyzeCsvData(String csvData, String reportType) {
        // TODO: Replace with real parsing and analysis logic
        String[] lines = csvData.split("\\r?\\n");
        return Map.of(
            "totalRows", Math.max(lines.length - 1, 0),
            "reportType", reportType,
            "generatedAt", Instant.now().toString(),
            "sampleData", lines.length > 1 ? lines[1] : "No data"
        );
    }

    private void sendReportEmail(String reportId, List<String> subscribers, JsonNode reportSummary) {
        // TODO: Replace with real email sending logic
        logger.info("[sendReportEmail] Sending report {} to {} subscribers, summary: {}", reportId, subscribers.size(), reportSummary);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) { }
        logger.info("[sendReportEmail] Email sent for report {}", reportId);
    }
}