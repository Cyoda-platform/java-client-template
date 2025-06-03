```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-entities")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    private static final String ENTITY_NAME = "cyodaEntity";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entity {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private JsonNode apiUrl;
        private JsonNode fetchedData;
        private Instant fetchedAt;
    }

    @Data
    @NoArgsConstructor
    public static class ApiUrlRequestDto {
        @NotBlank
        @Pattern(regexp = "^https?://.+", message = "api_url must be a valid HTTP/HTTPS URL")
        private String api_url;
    }

    /**
     * Workflow function to process cyodaEntity before persistence.
     * This function can modify the entity state as needed.
     * It returns the processed entity.
     */
    private Entity processcyodaEntity(Entity entity) {
        // Example: Add logic here if needed to modify entity before persistence.
        // Currently, it returns the entity unchanged.
        return entity;
    }

    @PostMapping
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid ApiUrlRequestDto request) {
        JsonNode apiUrlNode = objectMapper.createObjectNode().put("url", request.getApi_url());
        Entity entity = new Entity(null, apiUrlNode, null, null);

        // Pass workflow function processcyodaEntity as argument to addItem
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entity,
                this::processcyodaEntity
        );
        UUID technicalId = idFuture.join();
        entity.setTechnicalId(technicalId);
        logger.info("Created entity with technicalId {}", technicalId);
        CompletableFuture.runAsync(() -> fetchAndUpdateEntityData(entity))
                .exceptionally(ex -> {
                    logger.error("Error fetching data on entity creation: {}", ex.getMessage());
                    return null;
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PostMapping("/{id}")
    public ResponseEntity<Entity> updateEntityApiUrl(@PathVariable UUID id, @RequestBody @Valid ApiUrlRequestDto request) {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.join();
        if (itemNode == null) {
            logger.error("Entity with technicalId {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        Entity entity = objectMapper.convertValue(itemNode, Entity.class);
        JsonNode apiUrlNode = objectMapper.createObjectNode().put("url", request.getApi_url());
        entity.setApiUrl(apiUrlNode);

        // Note: Assuming updateItem signature has not changed or does not require workflow function.
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id,
                entity
        );
        updatedIdFuture.join(); // wait for update
        logger.info("Updated api_url for entity with technicalId {}", id);
        CompletableFuture.runAsync(() -> fetchAndUpdateEntityData(entity))
                .exceptionally(ex -> {
                    logger.error("Error fetching data on entity update: {}", ex.getMessage());
                    return null;
                });
        return ResponseEntity.ok(entity);
    }

    @PostMapping("/{id}/fetch")
    public ResponseEntity<Entity> manualFetch(@PathVariable UUID id) {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.join();
        if (itemNode == null) {
            logger.error("Entity with technicalId {} not found for manual fetch", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        Entity entity = objectMapper.convertValue(itemNode, Entity.class);
        fetchAndUpdateEntityData(entity);
        logger.info("Manual fetch completed for entity with technicalId {}", id);
        return ResponseEntity.ok(entity);
    }

    @GetMapping
    public ResponseEntity<List<Entity>> getAllEntities() {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();
        List<Entity> entities = new ArrayList<>();
        if (items != null) {
            for (JsonNode node : items) {
                Entity entity = objectMapper.convertValue(node, Entity.class);
                entities.add(entity);
            }
        }
        logger.info("Retrieving all entities, count: {}", entities.size());
        return ResponseEntity.ok(entities);
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Map<String, String>> deleteEntity(@PathVariable UUID id) {
        CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
        UUID deletedId = deletedIdFuture.join();
        if (deletedId == null) {
            logger.error("Entity with technicalId {} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        logger.info("Deleted entity with technicalId {}", id);
        return ResponseEntity.ok(Collections.singletonMap("message", "Entity deleted successfully."));
    }

    @PostMapping("/deleteAll")
    public ResponseEntity<Map<String, String>> deleteAllEntities() {
        CompletableFuture<ArrayNode> deletedItemsInfoFuture = entityService.deleteItems(ENTITY_NAME, ENTITY_VERSION);
        deletedItemsInfoFuture.join();
        logger.info("Deleted all entities");
        return ResponseEntity.ok(Collections.singletonMap("message", "All entities deleted successfully."));
    }

    private void fetchAndUpdateEntityData(Entity entity) {
        JsonNode urlNode = entity.getApiUrl() != null ? entity.getApiUrl().get("url") : null;
        if (urlNode == null || !urlNode.isTextual()) {
            logger.error("Invalid api_url format in entity {}", entity.getTechnicalId());
            return;
        }
        String url = urlNode.asText();
        try {
            new URI(url); // validate URI
            logger.info("Fetching data from external API for entity {}: {}", entity.getTechnicalId(), url);
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                logger.error("Received null response from external API for entity {}", entity.getTechnicalId());
                return;
            }
            JsonNode fetchedJson = objectMapper.readTree(response);
            entity.setFetchedData(fetchedJson);
            entity.setFetchedAt(Instant.now());
            entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, entity.getTechnicalId(), entity).join();
            logger.info("Fetched data updated for entity {}", entity.getTechnicalId());
        } catch (Exception e) {
            logger.error("Failed to fetch data for entity {}: {}", entity.getTechnicalId(), e.getMessage());
            // TODO: Consider retry or error status update in a real implementation
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Collections.singletonMap("error", ex.getStatusCode().toString()));
    }
}
```
**Explanation:**

- Added a private workflow function `processcyodaEntity` with the required prefix `process` followed by the entity name `cyodaEntity`.
- This function accepts an `Entity` object, can modify it, and returns it.
- Updated the `createEntity` method to pass this workflow function as the fourth argument to `entityService.addItem`.
- Other methods remain unchanged as per your instructions; only `addItem` usage is updated to include the workflow function.