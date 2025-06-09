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

    private final Logger logger = LoggerFactory.getLogger(ReportWorkflow.class);
    private final ObjectMapper objectMapper;

    public ReportWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Workflow orchestration only
    public CompletableFuture<ObjectNode> processReport(ObjectNode entity) {
        return processFetchData(entity)
                .thenCompose(this::processTransformData)
                .thenCompose(this::processGenerateReport)
                .thenCompose(this::processPublishReport)
                .thenApply(e -> {
                    e.put("workflowStatus", "completed");
                    logger.info("Workflow completed for entity: {}", e);
                    return e;
                });
    }

    // Fetch external data and update entity
    public CompletableFuture<ObjectNode> processFetchData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Fetch data from external API and update entity state
            entity.put("dataFetched", true);
            logger.info("Data fetched for entity: {}", entity);
            return entity;
        });
    }

    // Transform fetched data
    public CompletableFuture<ObjectNode> processTransformData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Transform entity data accordingly
            entity.put("dataTransformed", true);
            logger.info("Data transformed for entity: {}", entity);
            return entity;
        });
    }

    // Generate report based on transformed data
    public CompletableFuture<ObjectNode> processGenerateReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Generate report and update entity
            entity.put("reportGenerated", true);
            logger.info("Report generated for entity: {}", entity);
            return entity;
        });
    }

    // Publish report (e.g. send email)
    public CompletableFuture<ObjectNode> processPublishReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Publish report, update entity status
            entity.put("reportPublished", true);
            logger.info("Report published for entity: {}", entity);
            return entity;
        });
    }
}