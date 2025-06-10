package com.java_template.entity.report;

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

    // Workflow orchestration only
    public CompletableFuture<ObjectNode> processReport(ObjectNode entity) {
        return processFetchComments(entity)
                .thenCompose(this::processAnalyzeComments)
                .thenCompose(this::processSendEmail)
                .thenApply(report -> {
                    logger.info("Workflow complete for reportId: {}", report.get("reportId").asText());
                    return report;
                });
    }

    // Fetch comments from external source (mocked here)
    private CompletableFuture<ObjectNode> processFetchComments(ObjectNode entity) {
        // TODO: Replace with actual fetch logic if needed
        logger.info("Fetching comments for postId: {}", entity.get("postId").asInt());
        // For prototyping, no changes to entity
        return CompletableFuture.completedFuture(entity);
    }

    // Analyze comments and update entity state
    private CompletableFuture<ObjectNode> processAnalyzeComments(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                double sentimentScore = Math.random();
                String analysisSummary = sentimentScore < 0.5 ? "Negative Sentiment" : "Positive Sentiment";

                entity.put("analysisSummary", analysisSummary);
                entity.put("sentimentScore", sentimentScore);
                entity.putArray("keywords").removeAll()
                        .add("keyword1")
                        .add("keyword2");

                logger.info("Analysis complete for postId: {}", entity.get("postId").asInt());
            } catch (Exception e) {
                logger.error("Error during analysis", e);
            }
            return entity;
        });
    }

    // Send email (fire and forget)
    private CompletableFuture<ObjectNode> processSendEmail(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Implement actual email sending here
            logger.info("Sending email report for postId: {}", entity.get("postId").asInt());
            return entity;
        });
    }
}