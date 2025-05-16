package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Workflow(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // Orchestration method with no business logic, calls other process* methods
    public CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        return processNormalizeStatus(petEntity)
                .thenCompose(this::processAddProcessedTimestamp)
                .thenCompose(this::processLogPetInfo)
                .thenCompose(this::processAddPetLog);
    }

    private CompletableFuture<ObjectNode> processNormalizeStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.has("status") && !entity.get("status").isNull()) {
                entity.put("status", entity.get("status").asText().toLowerCase(Locale.ROOT));
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAddProcessedTimestamp(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("processedTimestamp", System.currentTimeMillis());
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processLogPetInfo(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String name = entity.path("name").asText("N/A");
            String status = entity.path("status").asText("N/A");
            logger.info("Pet name: {}, status: {}", name, status);
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAddPetLog(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode petLog = objectMapper.createObjectNode();
                petLog.put("petId", entity.path("id").asLong(-1));
                petLog.put("event", "Pet processed");
                petLog.put("timestamp", System.currentTimeMillis());

                entityService.addItem("petLog", ENTITY_VERSION, petLog, Function.identity())
                        .exceptionally(ex -> {
                            logger.error("Failed to add petLog entity", ex);
                            return null;
                        });
            } catch (Exception e) {
                logger.error("Exception in processAddPetLog", e);
            }
            return entity;
        });
    }
}