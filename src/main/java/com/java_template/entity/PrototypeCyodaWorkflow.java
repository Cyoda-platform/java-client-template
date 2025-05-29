package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entities")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String EXTERNAL_PET_API_FIND_BY_STATUS =
            "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST endpoint to create a single entity with workflow
    @PostMapping
    public CompletableFuture<UUID> createEntity(@RequestBody @Valid TransformedPet data) {
        ObjectNode entityNode = objectMapper.valueToTree(data);
        return entityService.addItem(
                "CyodaEntity",
                ENTITY_VERSION,
                entityNode,
                this::processCyodaEntity
        );
    }

    // POST batch endpoint for multiple entities; no workflow support assumed for batch in this example
    @PostMapping("/batch")
    public CompletableFuture<List<UUID>> createEntities(@RequestBody @Valid List<TransformedPet> data) {
        // Convert each DTO to ObjectNode
        List<ObjectNode> entityNodes = new ArrayList<>(data.size());
        for (TransformedPet pet : data) {
            entityNodes.add(objectMapper.valueToTree(pet));
        }
        // No workflow applied here as batch addItem overload with workflow is not specified
        return entityService.addItems("CyodaEntity", ENTITY_VERSION, entityNodes);
    }

    // GET single entity by id
    @GetMapping("/{id}")
    public CompletableFuture<TransformedPet> getEntity(@PathVariable UUID id) {
        return entityService.getItem("CyodaEntity", ENTITY_VERSION, id)
                .thenApply(objectNode -> {
                    try {
                        return objectMapper.treeToValue(objectNode, TransformedPet.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Mapping error", e);
                    }
                });
    }

    // GET all entities
    @GetMapping
    public CompletableFuture<List<TransformedPet>> getAllEntities() {
        return entityService.getItems("CyodaEntity", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<TransformedPet> list = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        try {
                            list.add(objectMapper.treeToValue(node, TransformedPet.class));
                        } catch (Exception e) {
                            throw new RuntimeException("Mapping error", e);
                        }
                    });
                    return list;
                });
    }

    // GET entities by condition param (condition is a string, e.g. JSON or query language)
    @GetMapping("/search")
    public CompletableFuture<List<TransformedPet>> getEntitiesByCondition(@RequestParam String condition) {
        return entityService.getItemsByCondition("CyodaEntity", ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    List<TransformedPet> list = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        try {
                            list.add(objectMapper.treeToValue(node, TransformedPet.class));
                        } catch (Exception e) {
                            throw new RuntimeException("Mapping error", e);
                        }
                    });
                    return list;
                });
    }

    // PUT to update existing entity by id; no workflow on update for now
    @PutMapping("/{id}")
    public CompletableFuture<UUID> updateEntity(@PathVariable UUID id, @RequestBody @Valid TransformedPet data) {
        ObjectNode entityNode = objectMapper.valueToTree(data);
        return entityService.updateItem("CyodaEntity", ENTITY_VERSION, id, entityNode);
    }

    // DELETE entity by id
    @DeleteMapping("/{id}")
    public CompletableFuture<UUID> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem("CyodaEntity", ENTITY_VERSION, id);
    }

    /*
     * Workflow function that asynchronously processes the entity before persisting.
     * It modifies the entity in-place.
     * It can perform async calls, add related entities of different models, enrich data, etc.
     * It must not add/update/delete the same entity model to avoid infinite recursion.
     * Must return the entity back at the end.
     */
    private CompletableFuture<ObjectNode> processCyodaEntity(ObjectNode entity) {
        logger.info("Workflow start for entity: {}", entity);

        // Defensive check: ensure entity is not null
        if (entity == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Normalize availability field: trim and uppercase
        String availability = entity.path("availability").asText(null);
        if (availability != null) {
            entity.put("availability", availability.trim().toUpperCase());
        }

        // Example: enrich entity with a timestamp field for auditing
        entity.put("processedAt", Instant.now().toString());

        // Prepare to fetch external pets by status (availability) for enrichment or supplementary storage
        String statusFilter = availability != null ? availability.toLowerCase() : "available";

        URI externalApiUri = URI.create(EXTERNAL_PET_API_FIND_BY_STATUS.replace("{status}", statusFilter));

        // Use RestTemplate to call external API asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                RestTemplate restTemplate = new RestTemplate();
                JsonNode response = restTemplate.getForObject(externalApiUri, JsonNode.class);
                return response;
            } catch (Exception e) {
                logger.error("Failed to fetch external pets: {}", e.toString());
                return null;
            }
        }).thenCompose(response -> {
            if (response == null || !response.isArray()) {
                logger.warn("No external data or invalid response");
                return CompletableFuture.completedFuture(entity);
            }

            List<CompletableFuture<UUID>> addFutures = new ArrayList<>();

            for (JsonNode petNode : response) {
                ObjectNode extPetEntity = objectMapper.createObjectNode();
                extPetEntity.put("name", petNode.path("name").asText(""));
                extPetEntity.put("species", petNode.path("species").asText(""));
                extPetEntity.put("categoryId", petNode.path("category").path("id").asInt(-1));
                extPetEntity.put("status", petNode.path("status").asText("unknown"));
                extPetEntity.put("source", "external");
                extPetEntity.put("fetchedAt", Instant.now().toString());

                // Add supplementary entity of model "ExternalPet" asynchronously; no workflow on this secondary entity
                CompletableFuture<UUID> addFuture = entityService.addItem(
                        "ExternalPet",
                        ENTITY_VERSION,
                        extPetEntity,
                        e -> CompletableFuture.completedFuture(e) // no-op workflow for external pets
                );
                addFutures.add(addFuture);
            }

            // Wait for all supplementary entities to be stored before completing
            return CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0]))
                    .handle((ignored, ex) -> {
                        if (ex != null) {
                            logger.error("Error adding supplementary ExternalPet entities: {}", ex.toString());
                        }
                        return entity;
                    });
        }).exceptionally(ex -> {
            logger.error("Unhandled workflow error: {}", ex.toString());
            return entity; // Return entity even on error to prevent blocking persistence
        });
    }

    // DTO classes

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransformedPet {
        @NotBlank
        private String name;

        @NotBlank
        private String species;

        @PositiveOrZero
        private Integer categoryId;

        @NotBlank
        private String availability;

        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
    }
}