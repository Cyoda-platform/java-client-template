package com.java_template.entity.cyoda;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("cyoda")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    // Condition function: check if entity has non-null "id" field to allow processing
    public CompletableFuture<Boolean> canProcess(ObjectNode entity) {
        boolean canProcess = entity.hasNonNull("id");
        logger.info("canProcess check: {}", canProcess);
        return CompletableFuture.completedFuture(canProcess);
    }

    // Action function: main processing workflow step
    public CompletableFuture<ObjectNode> processCyoda(ObjectNode entity) {
        logger.info("Starting processCyoda for entity id: {}", entity.has("id") ? entity.get("id").asText() : "unknown");

        // Add processed timestamp
        entity.put("processedTimestamp", Instant.now().toString());

        // Asynchronously enrich entity with supplementary data count
        CompletableFuture<Void> enrichmentFuture = searchSupplementaryCount()
                .thenAccept(count -> {
                    entity.put("supplementaryCount", count);
                    logger.info("Enrichment finished, supplementaryCount: {}", count);
                })
                .exceptionally(ex -> {
                    logger.error("Error in enrichment during processCyoda workflow", ex);
                    return null; // don't fail workflow, just log and continue
                });

        return enrichmentFuture.thenApply(v -> entity);
    }

    // Helper method to simulate external call to get supplementary count
    private CompletableFuture<Integer> searchSupplementaryCount() {
        // TODO: Replace with real call to entityService.searchItems or external API
        // Simulate async call returning count of supplementaryModel items for ENTITY_VERSION
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulated delay
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Mocked count value
            return 42;
        });
    }
}