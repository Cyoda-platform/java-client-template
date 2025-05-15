package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.controller.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@RequiredArgsConstructor
public class Workflow {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    // Orchestrates the workflow, no business logic here
    public CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        return processEnrichDescription(petEntity)
                .thenCompose(this::processAddProcessingLog);
    }

    // Enrich description if missing or empty
    private CompletableFuture<ObjectNode> processEnrichDescription(ObjectNode petEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!petEntity.hasNonNull("description") || petEntity.get("description").asText().trim().isEmpty()) {
                    String type = petEntity.has("type") ? petEntity.get("type").asText() : "unknown";
                    String name = petEntity.has("name") ? petEntity.get("name").asText() : "unknown";
                    String enrichedDescription = "A lovely " + type + " named " + name + ".";
                    petEntity.put("description", enrichedDescription);
                    logger.debug("Enriched description for pet: {}", enrichedDescription);
                }
                return petEntity;
            } catch (Exception e) {
                logger.error("Error in processEnrichDescription", e);
                return petEntity;
            }
        });
    }

    // Add supplementary processing log entity (different entity model)
    private CompletableFuture<ObjectNode> processAddProcessingLog(ObjectNode petEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode logEntity = objectMapper.createObjectNode();
                logEntity.put("timestamp", Instant.now().toString());
                String petName = petEntity.has("name") ? petEntity.get("name").asText() : "unknown";
                logEntity.put("message", "Pet processed in workflow, name=" + petName);
                String petType = petEntity.has("type") ? petEntity.get("type").asText() : "unknown";
                logEntity.put("petType", petType);
                // Fire-and-forget addItem call for log entity
                entityService.addItem("processingLog", ENTITY_VERSION, logEntity).exceptionally(ex -> {
                    logger.error("Failed to add processingLog entity", ex);
                    return null;
                });
                return petEntity;
            } catch (Exception e) {
                logger.error("Error in processAddProcessingLog", e);
                return petEntity;
            }
        });
    }
}