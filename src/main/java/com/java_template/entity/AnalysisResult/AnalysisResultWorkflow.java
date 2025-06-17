package com.java_template.entity.AnalysisResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class AnalysisResultWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisResultWorkflow.class);
    private final ObjectMapper objectMapper;

    public AnalysisResultWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestrates the workflow, no business logic here
    public CompletableFuture<ObjectNode> processAnalysisResult(ObjectNode entity) {
        String dataUrl = entity.hasNonNull("dataUrl") ? entity.get("dataUrl").asText() : null;
        if (dataUrl == null || dataUrl.isEmpty()) {
            entity.put("status", "failed");
            entity.put("errorMessage", "Missing dataUrl");
            return CompletableFuture.completedFuture(entity);
        }

        return processDownloadData(entity, dataUrl)
                .thenCompose(e -> processAnalyzeData(e))
                .thenCompose(e -> processComplete(e));
    }

    // Downloads data from the URL and attaches raw data to entity
    private CompletableFuture<ObjectNode> processDownloadData(ObjectNode entity, String dataUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode data = objectMapper.readTree(new URL(dataUrl));
                entity.set("downloadedData", data);
                entity.put("status", "downloaded");
                logger.info("Data downloaded from URL: {}", dataUrl);
            } catch (IOException e) {
                logger.error("Failed to download data", e);
                entity.put("status", "failed");
                entity.put("errorMessage", "Failed to download data: " + e.getMessage());
            }
            return entity;
        });
    }

    // Performs analysis on the downloaded data attached to entity
    private CompletableFuture<ObjectNode> processAnalyzeData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.has("downloadedData")) {
                entity.put("status", "failed");
                entity.put("errorMessage", "No data to analyze");
                return entity;
            }
            // Simulate analysis logic
            JsonNode data = entity.get("downloadedData");
            logger.info("Analyzing data: {}", data.toString());

            // Example analysis result
            entity.put("status", "analyzed");
            entity.put("report", "Sample analysis result");
            return entity;
        });
    }

    // Finalize the workflow, cleanup or any final state changes
    private CompletableFuture<ObjectNode> processComplete(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.has("report")) {
                entity.put("status", "failed");
                entity.put("errorMessage", "No analysis report generated");
            } else {
                entity.put("status", "completed");
            }
            // Remove downloaded data to save space
            entity.remove("downloadedData");
            logger.info("Workflow completed with status: {}", entity.get("status").asText());
            return entity;
        });
    }
}