package com.java_template.entity.Report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ReportWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(ReportWorkflow.class);
    private final ObjectMapper objectMapper;

    public ReportWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processReport(ObjectNode entity) {
        return processDownload(entity)
                .thenCompose(this::processAnalyze)
                .thenCompose(this::processSend)
                .exceptionally(ex -> {
                    logger.error("Error in workflow orchestration: {}", ex.getMessage());
                    return entity;
                });
    }

    // Simulate downloading CSV and setting initial state
    private CompletableFuture<ObjectNode> processDownload(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulated delay for download
                Thread.sleep(1000);
                // Set version or state attribute for example
                entity.put("downloadStatus", "completed");
                entity.put(ENTITY_VERSION, entity.has(ENTITY_VERSION) ? entity.get(ENTITY_VERSION).asInt() + 1 : 1);
                logger.info("Download completed, version set to {}", entity.get(ENTITY_VERSION).asInt());
            } catch (InterruptedException e) {
                logger.error("Error during download process", e);
                entity.put("downloadStatus", "failed");
            }
            return entity;
        });
    }

    // Simulate data analysis modifying entity content
    private CompletableFuture<ObjectNode> processAnalyze(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(2000);
                String content = entity.has("content") ? entity.get("content").asText() : "";
                entity.put("content", content + " - Analyzed");
                entity.put("analysisStatus", "completed");
                logger.info("Analysis completed");
            } catch (InterruptedException e) {
                logger.error("Error during analyze process", e);
                entity.put("analysisStatus", "failed");
            }
            return entity;
        });
    }

    // Simulate sending report to subscribers
    private CompletableFuture<ObjectNode> processSend(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000);
                entity.put("sendStatus", "completed");
                logger.info("Report sent successfully");
            } catch (InterruptedException e) {
                logger.error("Error during send process", e);
                entity.put("sendStatus", "failed");
            }
            return entity;
        });
    }
}