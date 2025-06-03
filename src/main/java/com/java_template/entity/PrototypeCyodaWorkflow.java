```java
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
import java.util.function.Function;

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
        // Do not expose technicalId in JSON
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

    private JsonNode fetchExternalJson(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from external API");
            }
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Failed to fetch or parse external API URL {}: {}", url, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch or parse external API");
        }
    }

    /**
     * Workflow function to process the entity asynchronously before persistence.
     * This function can modify the entity state or interact with other entities,
     * but must not add/update/delete entities of the same ENTITY_NAME to avoid recursion.
     *
     * @param entity the entity to process
     * @return the processed entity
     */
    private Entity processCyoda_entity(Entity entity) {
        // Example: here we could modify the entity, log, or trigger other async operations
        // For demonstration, we'll just return the entity as-is.
        // You can add your custom entity processing logic here.
        return entity;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid ApiUrlRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received request to create entity with api_url: {}", request.getApi_url());
        validateUrl(request.getApi_url());

        Entity entity = new Entity();
        entity.setApi_url(objectMapper.convertValue(request.getApi_url(), JsonNode.class));

        JsonNode fetchedData = fetchExternalJson(request.getApi_url());
        entity.setFetched_data(fetchedData);
        entity.setFetched_at(Instant.now());

        // Pass the workflow function processCyoda_entity as the workflow argument
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entity,
                this::processCyoda_entity
        );
        UUID technicalId = idFuture.get();
        entity.setTechnicalId(technicalId);

        logger.info("Created entity {} and fetched data from external API", technicalId);
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

        Entity entity = objectMapper.convertValue(existingNode, Entity.class);
        entity.setTechnicalId(id);

        entity.setApi_url(objectMapper.convertValue(request.getApi_url(), JsonNode.class));
        JsonNode fetchedData = fetchExternalJson(request.getApi_url());
        entity.setFetched_data(fetchedData);
        entity.setFetched_at(Instant.now());

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, entity);
        UUID updatedId = updatedItemId.get();
        entity.setTechnicalId(updatedId);

        logger.info("Updated entity {} and refreshed fetched data", updatedId);
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

        Entity entity = objectMapper.convertValue(existingNode, Entity.class);
        entity.setTechnicalId(id);

        JsonNode apiUrlNode = entity.getApi_url();
        if (apiUrlNode == null || !apiUrlNode.isTextual()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid stored api_url");
        }

        String url = apiUrlNode.asText();
        validateUrl(url);

        JsonNode fetchedData = fetchExternalJson(url);
        entity.setFetched_data(fetchedData);
        entity.setFetched_at(Instant.now());

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, entity);
        UUID updatedId = updatedItemId.get();
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
                entity.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
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
        // Optionally verify the deletion result if needed
        logger.info("Deleted all entities, result: {}", resultArray.toString());
        return ResponseEntity.noContent().build();
    }
}
```

### Explanation of changes:
- Added a private method `processCyoda_entity(Entity entity)` which implements the workflow function required by the new `entityService.addItem` signature.
- Updated the `createEntity` method to call `entityService.addItem` with the new 4-argument method, passing the workflow function as the last argument using a method reference `this::processCyoda_entity`.
- The workflow function currently returns the entity as-is but can be modified to include any asynchronous processing logic needed before persistence.
- The method name `processCyoda_entity` follows the required prefix `process` plus the entity name `cyoda_entity` (matching the `ENTITY_NAME` constant).

No other code changes were needed since this is the only place `addItem` is called.