package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "cyoda_entity";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entity {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private JsonNode api_url;
        private JsonNode fetched_data;
        private Instant fetched_at;
    }

    @Data
    public static class ApiUrlRequest {
        @NotBlank(message = "api_url must not be blank")
        private String api_url;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error processing request: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    private void validateUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url must not be empty");
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url is not a valid URL");
        }
    }

    /**
     * Workflow function to process the entity asynchronously before persistence.
     * Modifies entity state by fetching data from api_url and setting fetched_data and fetched_at.
     * Does not invoke add/update/delete on the same entity model to avoid recursion.
     */
    private CompletableFuture<ObjectNode> processCyoda_entity(ObjectNode entityNode) {
        JsonNode apiUrlNode = entityNode.get("api_url");
        if (apiUrlNode == null || !apiUrlNode.isTextual()) {
            // Invalid or missing api_url, clear fetched data and return entity unchanged
            entityNode.remove("fetched_data");
            entityNode.remove("fetched_at");
            return CompletableFuture.completedFuture(entityNode);
        }

        String url = apiUrlNode.asText();
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            logger.warn("Invalid api_url in workflow: {}", url);
            entityNode.remove("fetched_data");
            entityNode.remove("fetched_at");
            return CompletableFuture.completedFuture(entityNode);
        }

        // Fetch external JSON asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = restTemplate.getForObject(url, String.class);
                if (response == null) {
                    logger.warn("Empty response from external API in workflow for url: {}", url);
                    entityNode.remove("fetched_data");
                    entityNode.remove("fetched_at");
                    return entityNode;
                }
                JsonNode fetchedData = objectMapper.readTree(response);
                entityNode.set("fetched_data", fetchedData);
                entityNode.put("fetched_at", Instant.now().toString());
            } catch (Exception ex) {
                logger.error("Failed to fetch or parse external API URL {} in workflow: {}", url, ex.getMessage());
                entityNode.remove("fetched_data");
                entityNode.remove("fetched_at");
            }
            return entityNode;
        });
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid ApiUrlRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received request to create entity with api_url: {}", request.getApi_url());
        validateUrl(request.getApi_url());

        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("api_url", request.getApi_url());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entityNode,
                this::processCyoda_entity
        );
        UUID technicalId = idFuture.get();
        Entity entity = objectMapper.convertValue(entityNode, Entity.class);
        entity.setTechnicalId(technicalId);

        logger.info("Created entity {} with workflow enrichment", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Entity> updateEntity(@PathVariable UUID id, @RequestBody @Valid ApiUrlRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received request to update entity {} with new api_url: {}", id, request.getApi_url());
        validateUrl(request.getApi_url());

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode existingNode = itemFuture.get();
        if (existingNode == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        existingNode.put("api_url", request.getApi_url());

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id,
                existingNode,
                this::processCyoda_entity
        );
        UUID updatedId = updatedItemId.get();

        Entity entity = objectMapper.convertValue(existingNode, Entity.class);
        entity.setTechnicalId(updatedId);

        logger.info("Updated entity {} with workflow enrichment", updatedId);
        return ResponseEntity.ok(entity);
    }

    @PostMapping(value = "/{id}/fetch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Entity> fetchDataManually(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        logger.info("Received request to manually fetch data for entity {}", id);

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode existingNode = itemFuture.get();
        if (existingNode == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id,
                existingNode,
                this::processCyoda_entity
        );

        UUID updatedId = updatedIdFuture.get();

        Entity entity = objectMapper.convertValue(existingNode, Entity.class);
        entity.setTechnicalId(updatedId);

        logger.info("Manually fetched and updated data for entity {}", updatedId);
        return ResponseEntity.ok(entity);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Entity>> getAllEntities() throws ExecutionException, InterruptedException {
        logger.info("Received request to get all entities");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode itemsArray = itemsFuture.get();

        List<Entity> entities = new ArrayList<>();
        for (JsonNode node : itemsArray) {
            Entity entity = objectMapper.convertValue(node, Entity.class);
            if (node.has("technicalId")) {
                try {
                    entity.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                } catch (Exception e) {
                    // ignore parse error, leave technicalId null
                }
            }
            entities.add(entity);
        }
        return ResponseEntity.ok(entities);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete entity {}", id);
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
        UUID deletedId = deletedItemId.get();
        if (deletedId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        logger.info("Deleted entity {}", deletedId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllEntities() throws ExecutionException, InterruptedException {
        logger.info("Received request to delete all entities");
        CompletableFuture<ArrayNode> deletedItemsInfo = entityService.deleteItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode resultArray = deletedItemsInfo.get();
        logger.info("Deleted all entities, result: {}", resultArray.toString());
        return ResponseEntity.noContent().build();
    }
}