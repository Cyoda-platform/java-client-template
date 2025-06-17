package com.java_template.entity.Pet;

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
public class PetWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(PetWorkflow.class);

    private final ObjectMapper objectMapper;

    // Main workflow orchestration method, no business logic here
    public CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        return processAddDefaultAvailability(entity)
                .thenCompose(this::processFetchSupplementaryData);
    }

    // Add default availabilityStatus if missing
    public CompletableFuture<ObjectNode> processAddDefaultAvailability(ObjectNode entity) {
        if (!entity.has("availabilityStatus")) {
            entity.put("availabilityStatus", "unknown");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Simulate async fetching of supplementary data and update entity directly
    public CompletableFuture<ObjectNode> processFetchSupplementaryData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000); // simulate delay
                entity.put("supplementaryDataFetched", true);
                logger.info("Fetched and updated pet entity with supplementary data: {}", entity);
            } catch (InterruptedException e) {
                logger.error("Error fetching supplementary data", e);
                Thread.currentThread().interrupt();
            }
            return entity;
        });
    }
}