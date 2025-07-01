package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("pet")
@RequiredArgsConstructor
public class Workflow {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Condition function: returns true if syncFromPetstore flag is set true
    public CompletableFuture<ObjectNode> shouldSyncFromPetstore(ObjectNode entity) {
        boolean value = entity.has("syncFromPetstore") && entity.get("syncFromPetstore").asBoolean(false);
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: normalize status field to uppercase if present
    public CompletableFuture<ObjectNode> normalizeStatus(ObjectNode entity) {
        try {
            if (entity.hasNonNull("status")) {
                String status = entity.get("status").asText();
                entity.put("status", status.toUpperCase(Locale.ROOT));
            }
        } catch (Exception e) {
            log.error("Error in normalizeStatus: {}", e.getMessage(), e);
            entity.put("workflowError", e.getMessage());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: process sync from Petstore API, add petBackup entities asynchronously
    public CompletableFuture<ObjectNode> processPetSync(ObjectNode entity) {
        try {
            String typeFilter = entity.hasNonNull("type") ? entity.get("type").asText() : null;
            String statusFilter = entity.hasNonNull("status") ? entity.get("status").asText() : "available";

            URI uri = new URI("https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusFilter);
            log.info("processPetSync: Fetching pets from Petstore API: {}", uri);

            String raw = entityService.getRestTemplate().getForObject(uri, String.class);
            if (raw != null) {
                var root = objectMapper.readTree(raw);
                if (root.isArray()) {
                    for (var petNode : root) {
                        String petType = petNode.path("category").path("name").asText(null);
                        if (typeFilter != null && (petType == null || !typeFilter.equalsIgnoreCase(petType))) {
                            continue;
                        }
                        ObjectNode newPet = objectMapper.createObjectNode();
                        newPet.put("name", petNode.path("name").asText("Unnamed"));
                        newPet.put("type", petType);
                        newPet.putNull("age");
                        newPet.put("status", statusFilter.toUpperCase(Locale.ROOT));

                        try {
                            entityService.addItem("petBackup", ENTITY_VERSION, newPet, entityNode -> {
                                if (entityNode.hasNonNull("status")) {
                                    entityNode.put("status", entityNode.get("status").asText().toUpperCase(Locale.ROOT));
                                }
                                return entityNode;
                            });
                        } catch (Exception ex) {
                            log.warn("Failed to add petBackup entity during sync: {}", ex.toString());
                        }
                    }
                }
            }
            entity.remove("syncFromPetstore");
        } catch (Exception e) {
            log.error("Error in processPetSync: {}", e.getMessage(), e);
            entity.put("workflowError", e.getMessage());
        }
        return CompletableFuture.completedFuture(entity);
    }
}