package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class PrototypeWorkflow {

    public CompletableFuture<ObjectNode> processPrototype(ObjectNode entity) {
        log.info("Running processPrototype workflow for entity before persistence");

        // Prevent adding/updating/deleting entity of the same model here to avoid recursion
        // If needed, get/add/update/delete on other entityModels allowed

        // Add or update a timestamp field directly on the entity
        entity.put("processedTimestamp", System.currentTimeMillis());

        // Run async enrichment task (e.g., fetch supplementary data from another entity model)
        CompletableFuture<Void> enrichment = CompletableFuture.runAsync(() -> {
            try {
                // Example: fetch related entity and enrich current entity - pseudo-code
                // ObjectNode relatedEntity = entityService.getItem("relatedModel", ENTITY_VERSION, someUUID).join();
                // if (relatedEntity != null) entity.put("relatedData", relatedEntity.get("someField").asText());
                log.debug("Simulated async enrichment task completed");
            } catch (Exception e) {
                log.error("Error during async enrichment in processPrototype", e);
                // Continue despite errors
            }
        });

        // Fire-and-forget async side effect (e.g., logging, notifications)
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Async fire-and-forget task triggered from processPrototype");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("Error in async fire-and-forget task", e);
                Thread.currentThread().interrupt();
            }
        });

        // Wait for enrichment to complete before returning entity with modifications
        return enrichment.thenApply(v -> entity);
    }
}