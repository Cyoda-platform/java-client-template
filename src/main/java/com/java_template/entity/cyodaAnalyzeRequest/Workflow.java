package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class Workflow {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CompletableFuture<ObjectNode> processCyodaAnalyzeRequest(ObjectNode entity) {
        log.info("Workflow processCyodaAnalyzeRequest started for entity: {}", entity);

        if (!entity.hasNonNull("csvUrl")) {
            log.warn("Entity missing csvUrl, skipping workflow");
            return CompletableFuture.completedFuture(entity);
        }

        entity.put("id", entity.hasNonNull("id") ? entity.get("id").asText() : UUID.randomUUID().toString());
        entity.put("status", "processing");
        entity.put("requestedAt", Instant.now().toString());

        return processDownloadCsv(entity)
                .thenCompose(this::processAnalyzeCsv)
                .thenCompose(report -> processSendEmail(report, entity))
                .exceptionally(ex -> {
                    log.error("Workflow processing failed for taskId={}: {}", entity.get("id").asText(), ex.getMessage(), ex);
                    entity.put("status", "failed");
                    entity.put("errorMessage", ex.getMessage());
                    entity.put("failedAt", Instant.now().toString());
                    return entity;
                });
    }

    private CompletableFuture<ObjectNode> processDownloadCsv(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String taskId = entity.get("id").asText();
            String csvUrl = entity.get("csvUrl").asText();
            log.info("Downloading CSV for taskId={} from URL: {}", taskId, csvUrl);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(csvUrl))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to download CSV: HTTP " + response.statusCode());
                }
                ObjectNode result = entity.objectNode();
                result.put("taskId", taskId);
                result.put("csvData", response.body());
                return result;
            } catch (Exception e) {
                throw new RuntimeException("CSV download failed", e);
            }
        }, executor);
    }

    private CompletableFuture<ObjectNode> processAnalyzeCsv(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String taskId = entity.get("taskId").asText();
            log.info("Performing mock analysis for taskId={}", taskId);

            // Mock summary statistics and trends
            ObjectNode summaryStatistics = entity.objectNode();
            summaryStatistics.put("meanPrice", 500000.0);
            summaryStatistics.put("medianPrice", 450000.0);
            summaryStatistics.put("totalListings", 1000);

            ObjectNode basicTrends = entity.objectNode();
            basicTrends.put("priceTrend", "stable");
            if (summaryStatistics.get("meanPrice").asDouble() > 600000) {
                basicTrends.put("priceTrend", "increasing");
            }

            ObjectNode report = entity.objectNode();
            report.put("taskId", taskId);
            report.put("status", "completed");
            report.set("summaryStatistics", summaryStatistics);
            report.set("basicTrends", basicTrends);
            report.put("emailSent", false);

            return report;
        }, executor);
    }

    private CompletableFuture<ObjectNode> processSendEmail(ObjectNode report, ObjectNode entity) {
        return CompletableFuture.runAsync(() -> {
            String taskId = report.get("taskId").asText();
            log.info("Sending email report asynchronously for taskId={}", taskId);
            try {
                Thread.sleep(1000);
                report.put("emailSent", true);
                log.info("Email report sent for taskId={}", taskId);
            } catch (Exception e) {
                log.error("Failed to send email for taskId={}: {}", taskId, e.getMessage(), e);
            }
        }, executor).thenApply(v -> {
            entity.put("status", "completed");
            entity.put("completedAt", Instant.now().toString());
            return entity;
        });
    }
}