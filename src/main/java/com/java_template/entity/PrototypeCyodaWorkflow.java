package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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
     * It asynchronously fetches data from the apiUrl and updates fetchedData and fetchedAt fields.
     * It does not perform add/update/delete on the same entity to prevent infinite recursion.
     */
    private CompletableFuture<ObjectNode> processcyodaEntity(ObjectNode entityNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode apiUrlNode = entityNode.get("apiUrl");
                if (apiUrlNode == null || apiUrlNode.isNull() || !apiUrlNode.has("url")) {
                    logger.warn("Entity missing or invalid apiUrl, skipping fetch");
                    return entityNode;
                }
                String url = apiUrlNode.get("url").asText(null);
                if (url == null || url.isEmpty()) {
                    logger.warn("Entity apiUrl.url is empty, skipping fetch");
                    return entityNode;
                }

                // Validate URL format to avoid runtime exceptions
                new URI(url);

                logger.info("Workflow: fetching external data from {}", url);
                String response = restTemplate.getForObject(url, String.class);
                if (response == null) {
                    logger.warn("Workflow: external API response null for url {}", url);
                    return entityNode;
                }

                JsonNode fetchedDataJson = objectMapper.readTree(response);

                // Update fetchedData and fetchedAt fields
                entityNode.set("fetchedData", fetchedDataJson);
                entityNode.put("fetchedAt", Instant.now().toString());

                logger.info("Workflow: fetchedData and fetchedAt updated in entity");
            } catch (Exception e) {
                logger.error("Workflow: error fetching external data: {}", e.toString());
                // Optionally, set error info inside entityNode to indicate failure
                entityNode.put("fetchError", e.getMessage());
            }
            return entityNode;
        });
    }

    @PostMapping
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid ApiUrlRequestDto request) {
        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.putNull("technicalId");
        ObjectNode apiUrlNode = objectMapper.createObjectNode();
        apiUrlNode.put("url", request.getApi_url());
        entityNode.set("apiUrl", apiUrlNode);
        entityNode.putNull("fetchedData");
        entityNode.putNull("fetchedAt");
        entityNode.putNull("fetchError");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entityNode,
                this::processcyodaEntity
        );
        UUID technicalId = idFuture.join();
        entityNode.put("technicalId", technicalId.toString());

        Entity entity = objectMapper.convertValue(entityNode, Entity.class);
        logger.info("Created entity with technicalId {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PostMapping("/{id}")
    public ResponseEntity<Entity> updateEntityApiUrl(@PathVariable UUID id, @RequestBody @Valid ApiUrlRequestDto request) {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.join();
        if (itemNode == null) {
            logger.error("Entity with technicalId {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        // Update apiUrl field
        ObjectNode apiUrlNode = objectMapper.createObjectNode();
        apiUrlNode.put("url", request.getApi_url());
        itemNode.set("apiUrl", apiUrlNode);

        // Clear fetched data and errors as URL changed
        itemNode.putNull("fetchedData");
        itemNode.putNull("fetchedAt");
        itemNode.putNull("fetchError");

        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id,
                itemNode,
                this::processcyodaEntity
        );
        updatedIdFuture.join();

        Entity updatedEntity = objectMapper.convertValue(itemNode, Entity.class);
        logger.info("Updated api_url for entity with technicalId {}", id);
        return ResponseEntity.ok(updatedEntity);
    }

    @PostMapping("/{id}/fetch")
    public ResponseEntity<Entity> manualFetch(@PathVariable UUID id) {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.join();
        if (itemNode == null) {
            logger.error("Entity with technicalId {} not found for manual fetch", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        CompletableFuture<ObjectNode> processedFuture = processcyodaEntity(itemNode)
                .thenCompose(processedEntity -> entityService.updateItem(
                        ENTITY_NAME,
                        ENTITY_VERSION,
                        id,
                        processedEntity
                ));
        processedFuture.join();

        Entity updatedEntity = objectMapper.convertValue(itemNode, Entity.class);
        logger.info("Manual fetch completed for entity {}", id);
        return ResponseEntity.ok(updatedEntity);
    }

    @GetMapping
    public ResponseEntity<List<Entity>> getAllEntities() {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        List<Entity> entities = new ArrayList<>();
        if (items != null) {
            for (JsonNode node : items) {
                if (node.isObject()) {
                    entities.add(objectMapper.convertValue(node, Entity.class));
                }
            }
        }
        logger.info("Retrieved all entities, count: {}", entities.size());
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
        CompletableFuture<Void> deletedAllFuture = entityService.deleteItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(ignored -> null);
        deletedAllFuture.join();
        logger.info("Deleted all entities");
        return ResponseEntity.ok(Collections.singletonMap("message", "All entities deleted successfully."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Collections.singletonMap("error", ex.getReason()));
    }
}