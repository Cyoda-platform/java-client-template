package com.java_template.entity.analysisJob;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class AnalysisJobWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisJobWorkflow.class);

    private final ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processAnalysisJob(ObjectNode entity) {
        String jobId = UUID.randomUUID().toString();
        entity.put("jobId", jobId);
        entity.put("status", "queued");
        entity.put("requestedAt", Instant.now().toString());

        updateJobStatus(jobId, "queued", Instant.now());

        CompletableFuture.runAsync(() -> {
            try {
                updateJobStatus(jobId, "running", null);
                CompletableFuture<ObjectNode> csvDataFuture = processDownloadCsv(entity);
                ObjectNode csvDataNode = csvDataFuture.join();

                CompletableFuture<ObjectNode> reportFuture = processPerformAnalysis(entity, csvDataNode);
                ObjectNode reportNode = reportFuture.join();

                processPersistReportEntity(reportNode);

                updateJobStatus(jobId, "completed", null);
                entity.put("status", "completed");
            } catch (Exception ex) {
                logger.error("Error processing analysis job {}", jobId, ex);
                updateJobStatus(jobId, "failed", null);
                entity.put("status", "failed");
            }
        });

        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processDownloadCsv(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = entity.get("url").asText(DEFAULT_CSV_URL);
                // TODO: download CSV and parse into ObjectNode representation (mocked here)
                ObjectNode csvDataNode = objectMapper.createObjectNode();
                // Placeholder: set csvDataNode content accordingly
                return csvDataNode;
            } catch (Exception e) {
                throw new RuntimeException("Failed to download CSV", e);
            }
        });
    }

    public CompletableFuture<ObjectNode> processPerformAnalysis(ObjectNode entity, ObjectNode csvDataNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jobId = entity.get("jobId").asText();
                String analysisType = entity.has("analysisType") ? entity.get("analysisType").asText() : "summary";
                // TODO: perform analysis on csvDataNode, return report as ObjectNode (mocked here)
                ObjectNode reportNode = objectMapper.createObjectNode();
                reportNode.put("analysisId", jobId);
                reportNode.put("generatedAt", Instant.now().toString());
                ObjectNode summaryStats = objectMapper.createObjectNode();
                summaryStats.put("meanPrice", 0);
                summaryStats.put("medianPrice", 0);
                summaryStats.put("totalListings", 0);
                reportNode.set("summaryStatistics", summaryStats);
                return reportNode;
            } catch (Exception e) {
                throw new RuntimeException("Failed to perform analysis", e);
            }
        });
    }

    public void processPersistReportEntity(ObjectNode reportNode) {
        // TODO: persist the reportNode entity to database or storage
        logger.info("Persisting report entity with analysisId={}", reportNode.get("analysisId").asText());
    }

    private void updateJobStatus(String jobId, String status, Instant timestamp) {
        // TODO: update job status in persistent storage or monitoring system
        logger.info("Job {} status updated to {} at {}", jobId, status, timestamp != null ? timestamp.toString() : "n/a");
    }
}