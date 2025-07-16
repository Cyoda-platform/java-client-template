package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Component
public class PrototypeCyodaWorkflow {

    private final NotificationService notificationService;
    private final EntityService entityService;

    public PrototypeCyodaWorkflow(NotificationService notificationService, EntityService entityService) {
        this.notificationService = notificationService;
        this.entityService = entityService;
    }

    // Workflow function for Pet entity
    public CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        // Defensive: avoid null entity
        if (entity == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Set default description if missing or blank
        if (!entity.hasNonNull("description") || entity.get("description").asText().isBlank()) {
            entity.put("description", "No description available");
        }

        // Normalize name to trimmed string if present
        if (entity.hasNonNull("name")) {
            String name = entity.get("name").asText("").trim();
            entity.put("name", name);
        }

        // Example of adding supplementary entity async
        ObjectNode supplementaryData = createSupplementaryDataFrom(entity);
        CompletableFuture<UUID> suppFuture = entityService.addItem(
                "PetSupplementary", ENTITY_VERSION, supplementaryData);

        // Fire and forget notification email async call
        notificationService.sendPetCreatedEmailAsync(entity);

        // Return after supplementary entity added, entity is modified directly
        return suppFuture.thenApply(uuid -> entity);
    }

    private ObjectNode createSupplementaryDataFrom(ObjectNode entity) {
        ObjectNode supplementary = entity.objectNode();
        // Copy pet id if present, otherwise empty string
        String petId = entity.hasNonNull("id") ? entity.get("id").asText("") : "";
        supplementary.put("petId", petId);
        supplementary.put("info", "Supplementary info related to pet");
        // Add timestamp or other metadata if needed
        supplementary.put("createdAt", System.currentTimeMillis());
        return supplementary;
    }

    // Expose workflow function reference for controller usage
    public Function<ObjectNode, CompletableFuture<ObjectNode>> workflow() {
        return this::processPet;
    }
}


// Controller example showing usage of workflow function and clean async logic

package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/pets")
public class PetController {

    private final EntityService entityService;
    private final PrototypeCyodaWorkflow prototypeCyodaWorkflow;

    public PetController(EntityService entityService, PrototypeCyodaWorkflow prototypeCyodaWorkflow) {
        this.entityService = entityService;
        this.prototypeCyodaWorkflow = prototypeCyodaWorkflow;
    }

    // Constant for entity version
    private static final String ENTITY_VERSION = "1.0";
    private static final String ENTITY_NAME = "Pet";

    @PostMapping
    public CompletableFuture<ResponseEntity<UUID>> createPet(@RequestBody ObjectNode petEntity) {
        // Basic validation example
        if (petEntity == null || !petEntity.hasNonNull("name") || petEntity.get("name").asText().isBlank()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }

        // Pass workflow function to addItem - all async logic moved there
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petEntity, prototypeCyodaWorkflow.workflow())
                .thenApply(ResponseEntity::ok);
    }
}


// Example interfaces for EntityService and NotificationService to avoid missing references

package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface EntityService {
    CompletableFuture<UUID> addItem(String entityModel, String entityVersion, ObjectNode entity, Function<ObjectNode, CompletableFuture<ObjectNode>> workflow);
    // other methods omitted
}

package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface NotificationService {
    void sendPetCreatedEmailAsync(ObjectNode petEntity);
    // other notification methods omitted
}