package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    public CompletableFuture<ObjectNode> processPet(ObjectNode pet) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Orchestrate workflow steps here without business logic
                CompletableFuture<ObjectNode> future = processValidate(pet)
                        .thenCompose(this::processEnrich)
                        .thenCompose(this::processSideEffect);
                return future.get(); // blocking here is acceptable since inside supplyAsync
            } catch (Exception e) {
                logger.error("Error in processPet workflow orchestration", e);
                return pet;
            }
        });
    }

    private CompletableFuture<ObjectNode> processValidate(ObjectNode pet) {
        return CompletableFuture.supplyAsync(() -> {
            // Example validation: ensure required fields have defaults
            if (!pet.hasNonNull("name") || pet.get("name").asText().trim().isEmpty()) {
                pet.put("name", "unknown");
            }
            if (!pet.hasNonNull("type") || pet.get("type").asText().trim().isEmpty()) {
                pet.put("type", "unknown");
            }
            if (!pet.hasNonNull("status") || pet.get("status").asText().trim().isEmpty()) {
                pet.put("status", "unknown");
            }
            return pet;
        });
    }

    private CompletableFuture<ObjectNode> processEnrich(ObjectNode pet) {
        return CompletableFuture.supplyAsync(() -> {
            // Set default description if missing or empty
            if (!pet.hasNonNull("description") || pet.get("description").asText().trim().isEmpty()) {
                pet.put("description", "No description provided");
            }
            return pet;
        });
    }

    private CompletableFuture<ObjectNode> processSideEffect(ObjectNode pet) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String name = pet.hasNonNull("name") ? pet.get("name").asText() : "unknown";
                String status = pet.hasNonNull("status") ? pet.get("status").asText() : "unknown";
                logger.info("Async side-effect: notifying about pet '{}', status '{}'", name, status);
                // TODO: Insert async side-effect code here (e.g. event publish, email notification)
            } catch (Exception e) {
                logger.error("Error in async side-effect in workflow", e);
            }
            return pet;
        });
    }
}