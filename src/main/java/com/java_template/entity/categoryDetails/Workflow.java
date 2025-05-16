package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        // Orchestration only, no business logic here
        return processNormalizeStatus(entity)
            .thenCompose(this::processAddTimestamp)
            .thenCompose(this::processEnrichCategory)
            .exceptionally(ex -> {
                logger.error("Exception in processPet workflow", ex);
                return entity;
            });
    }

    private CompletableFuture<ObjectNode> processNormalizeStatus(ObjectNode entity) {
        try {
            if (entity.has("status") && entity.get("status").isTextual()) {
                String status = entity.get("status").asText(Locale.ROOT).toLowerCase(Locale.ROOT);
                entity.put("status", status);
            }
        } catch (Exception e) {
            logger.warn("Error in processNormalizeStatus", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAddTimestamp(ObjectNode entity) {
        try {
            entity.put("lastProcessedAt", System.currentTimeMillis());
        } catch (Exception e) {
            logger.warn("Error in processAddTimestamp", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processEnrichCategory(ObjectNode entity) {
        try {
            if (entity.has("category") && entity.get("category").isTextual()) {
                String categoryName = entity.get("category").asText();

                return entityService.getItem("categoryDetails", ENTITY_VERSION, categoryName)
                    .thenCompose(categoryDetailsNode -> {
                        if (categoryDetailsNode != null && !categoryDetailsNode.isEmpty(null)) {
                            entity.set("categoryDetails", categoryDetailsNode);
                        } else {
                            entity.putObject("categoryDetails").put("info", "No details available");
                        }

                        ObjectNode petEvent = entity.objectNode();
                        petEvent.put("eventType", "petProcessed");
                        petEvent.put("petName", entity.path("name").asText("unknown"));
                        petEvent.put("timestamp", System.currentTimeMillis());

                        // Fire-and-forget petEvents entity creation; log failures but do not block
                        return entityService.addItem("petEvents", ENTITY_VERSION, petEvent, Function.identity())
                            .handle((uuid, ex) -> {
                                if (ex != null) {
                                    logger.warn("Failed to add petEvent for pet {}: {}", entity.path("name").asText(), ex.toString());
                                } else {
                                    logger.debug("Added petEvent entity with id {}", uuid);
                                }
                                return entity;
                            });
                    });
            }
        } catch (Exception e) {
            logger.warn("Error in processEnrichCategory", e);
        }
        return CompletableFuture.completedFuture(entity);
    }
}