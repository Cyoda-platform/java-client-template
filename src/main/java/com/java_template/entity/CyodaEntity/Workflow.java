package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private static final String EXTERNAL_PET_API_FIND_BY_STATUS =
            "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate = new RestTemplate();

    // Main workflow orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processCyodaEntity(ObjectNode entity) {
        logger.info("Workflow start for entity: {}", entity);
        return processNormalizeAvailability(entity)
                .thenCompose(this::processEnrichTimestamp)
                .thenCompose(this::processFetchExternalPets)
                .exceptionally(ex -> {
                    logger.error("Unhandled workflow error: {}", ex.toString());
                    return entity;
                });
    }

    // Normalize availability: trim and uppercase availability field
    private CompletableFuture<ObjectNode> processNormalizeAvailability(ObjectNode entity) {
        if (entity == null) {
            return CompletableFuture.completedFuture(null);
        }
        String availability = entity.path("availability").asText(null);
        if (availability != null) {
            entity.put("availability", availability.trim().toUpperCase());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Add or update processedAt timestamp
    private CompletableFuture<ObjectNode> processEnrichTimestamp(ObjectNode entity) {
        if (entity == null) {
            return CompletableFuture.completedFuture(null);
        }
        entity.put("processedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }

    // Fetch external pets and add supplementary entities asynchronously
    private CompletableFuture<ObjectNode> processFetchExternalPets(ObjectNode entity) {
        if (entity == null) {
            return CompletableFuture.completedFuture(null);
        }

        String availability = entity.path("availability").asText(null);
        String statusFilter = availability != null ? availability.toLowerCase() : "available";
        URI externalApiUri = URI.create(EXTERNAL_PET_API_FIND_BY_STATUS.replace("{status}", statusFilter));

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode response = restTemplate.getForObject(externalApiUri, JsonNode.class);
                return response;
            } catch (Exception e) {
                logger.error("Failed to fetch external pets: {}", e.toString());
                return null;
            }
        }).thenCompose(response -> processAddSupplementaryPets(entity, response));
    }

    // Add supplementary ExternalPet entities; no changes to the main entity here
    private CompletableFuture<ObjectNode> processAddSupplementaryPets(ObjectNode entity, JsonNode response) {
        if (response == null || !response.isArray()) {
            logger.warn("No external data or invalid response");
            return CompletableFuture.completedFuture(entity);
        }

        List<CompletableFuture<Void>> addFutures = new ArrayList<>();

        for (JsonNode petNode : response) {
            ObjectNode extPetEntity = objectMapper.createObjectNode();
            extPetEntity.put("name", petNode.path("name").asText(""));
            extPetEntity.put("species", petNode.path("species").asText(""));
            extPetEntity.put("categoryId", petNode.path("category").path("id").asInt(-1));
            extPetEntity.put("status", petNode.path("status").asText("unknown"));
            extPetEntity.put("source", "external");
            extPetEntity.put("fetchedAt", Instant.now().toString());

            // TODO: Replace with actual asynchronous addItem call to entity service
            CompletableFuture<Void> addFuture = CompletableFuture.completedFuture(null);
            addFutures.add(addFuture);
        }

        return CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0]))
                .handle((ignored, ex) -> {
                    if (ex != null) {
                        logger.error("Error adding supplementary ExternalPet entities: {}", ex.toString());
                    }
                    return entity;
                });
    }
}