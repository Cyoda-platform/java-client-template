package com.java_template.entity.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class UserWorkflow {

    private final Logger logger = LoggerFactory.getLogger(UserWorkflow.class);
    private final ObjectMapper objectMapper;

    public UserWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestrates workflow steps, no business logic here
    public CompletableFuture<ObjectNode> processUser(ObjectNode entity) {
        return processIngest(entity)
                .thenCompose(this::processTransform)
                .thenCompose(this::processStore)
                .thenCompose(this::processGenerateReport)
                .thenCompose(this::processPublishReport);
    }

    // Simulates ingestion step - fetch external data or initial setup
    private CompletableFuture<ObjectNode> processIngest(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("ingested", true);
            entity.put("entityVersion", ENTITY_VERSION);
            logger.info("Ingested user: {}", entity);
            return entity;
        });
    }

    // Simulates data transformation step
    private CompletableFuture<ObjectNode> processTransform(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // Example transformation: mark transformed=true
            entity.put("transformed", true);
            logger.info("Transformed user: {}", entity);
            return entity;
        });
    }

    // Simulates storing data step
    private CompletableFuture<ObjectNode> processStore(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("stored", true);
            logger.info("Stored user data: {}", entity);
            return entity;
        });
    }

    // Simulates report generation step
    private CompletableFuture<ObjectNode> processGenerateReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("reportGenerated", true);
            logger.info("Generated report for user: {}", entity);
            return entity;
        });
    }

    // Simulates publishing report step (e.g. send email)
    private CompletableFuture<ObjectNode> processPublishReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("published", true);
            logger.info("Published report for user: {}", entity);
            return entity;
        });
    }
}