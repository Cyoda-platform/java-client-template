package com.java_template.entity.Report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class ReportWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(ReportWorkflow.class);
    private final ObjectMapper objectMapper;

    // Orchestrates the workflow, no business logic here
    public CompletableFuture<ObjectNode> processReport(ObjectNode entity) {
        return processValidateData(entity)
                .thenCompose(this::processAnalyzeData)
                .thenCompose(this::processGenerateReport)
                .thenCompose(this::processNotifyCompletion);
    }

    // Validate input data, update entity state accordingly
    public CompletableFuture<ObjectNode> processValidateData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Validating report entity: {}", entity);
            // Example validation logic, set validation flag on entity
            boolean valid = entity.has("analysis") && !entity.get("analysis").isNull();
            entity.put("valid", valid);
            logger.info("Validation result: {}", valid);
            return entity;
        });
    }

    // Analyze the data, update entity with analysis results
    public CompletableFuture<ObjectNode> processAnalyzeData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Analyzing data for entity: {}", entity);
            // Simulate analysis logic and add dummy results
            ObjectNode analysis = objectMapper.createObjectNode();
            analysis.put("highPerformingProducts", "product1,product2");
            analysis.put("lowStockProducts", "product3");
            analysis.put("trends", "upward");
            entity.set("analysisResults", analysis);
            logger.info("Analysis complete: {}", analysis);
            return entity;
        });
    }

    // Generate report based on analysis, update entity state
    public CompletableFuture<ObjectNode> processGenerateReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Generating report for entity: {}", entity);
            // Simulate report generation
            entity.put("reportGenerated", true);
            entity.put("entityVersion", ENTITY_VERSION);
            logger.info("Report generation completed");
            return entity;
        });
    }

    // Notify or finalize workflow, e.g. send email or mark complete
    public CompletableFuture<ObjectNode> processNotifyCompletion(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Notifying completion for entity: {}", entity);
            // Simulate notification step - just update state here
            entity.put("notificationSent", true);
            logger.info("Notification sent");
            return entity;
        });
    }
}