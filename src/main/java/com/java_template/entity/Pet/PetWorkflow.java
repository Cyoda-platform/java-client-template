package com.java_template.entity.Pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class PetWorkflow {

    private final ObjectMapper objectMapper;

    // Orchestrates the workflow steps without business logic
    public CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        return processSetAvailabilityStatus(entity)
                .thenCompose(this::processAddSupplementaryData);
    }

    // Sets availability status attribute directly on the entity
    public CompletableFuture<ObjectNode> processSetAvailabilityStatus(ObjectNode entity) {
        entity.put("availabilityStatus", "Available");
        return CompletableFuture.completedFuture(entity);
    }

    // Adds supplementary data attribute asynchronously and modifies entity directly
    public CompletableFuture<ObjectNode> processAddSupplementaryData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String supplementaryData = fetchSupplementaryData(entity);
            entity.put("additionalInfo", supplementaryData);
            return entity;
        });
    }

    // Placeholder for fetching supplementary data
    private String fetchSupplementaryData(ObjectNode entity) {
        // TODO: Implement actual supplementary data fetching logic
        return "Supplementary Data";
    }
}