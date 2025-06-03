```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/entities")
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory store to mock persistence: id -> Entity
    private final Map<UUID, Entity> entities = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entity {
        private UUID id;
        private JsonNode apiUrl;
        private JsonNode fetchedData;
        private Instant fetchedAt;
    }

    @Data
    @NoArgsConstructor
    public static class ApiUrlRequest {
        private JsonNode api_url;
    }

    // 1. Create Entity & Fetch Data
    @PostMapping
    public ResponseEntity<Entity> createEntity(@RequestBody ApiUrlRequest request) {
        if (request == null || request.getApi_url() == null || !isApiUrlValid(request.getApi_url())) {
            logger.error("Invalid or missing api_url in createEntity request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or missing api_url");
        }

        UUID id = UUID.randomUUID();
        Entity entity = new Entity();
        entity.setId(id);
        entity.setApiUrl(request.getApi_url());

        // Fire-and-forget fetch to keep controller responsive.
        CompletableFuture.runAsync(() -> fetchAndUpdateEntityData(entity))
                .exceptionally(ex -> {
                    logger.error("Error fetching data on entity creation: {}", ex.getMessage());
                    return null;
                });

        // Save entity immediately with no fetchedData/fetchedAt yet
        entities.put(id, entity);
        logger.info("Created entity with id {}", id);

        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    // 2. Update Entity API URL & Fetch Data
    @PostMapping("/{id}")
    public ResponseEntity<Entity> updateEntityApiUrl(@PathVariable UUID id, @RequestBody ApiUrlRequest request) {
        if (request == null || request.getApi_url() == null || !isApiUrlValid(request.getApi_url())) {
            logger.error("Invalid or missing api_url in updateEntityApiUrl request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or missing api_url");
        }

        Entity entity = entities.get(id);
        if (entity == null) {
            logger.error("Entity with id {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        entity.setApiUrl(request.getApi_url());

        CompletableFuture.runAsync(() -> fetchAndUpdateEntityData(entity))
                .exceptionally(ex -> {
                    logger.error("Error fetching data on entity update: {}", ex.getMessage());
                    return null;
                });

        logger.info("Updated api_url for entity with id {}", id);

        return ResponseEntity.ok(entity);
    }

    // 3. Manual Fetch Data for Entity
    @PostMapping("/{id}/fetch")
    public ResponseEntity<Entity> manualFetch(@PathVariable UUID id) {
        Entity entity = entities.get(id);
        if (entity == null) {
            logger.error("Entity with id {} not found for manual fetch", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        try {
            fetchAndUpdateEntityData(entity);
            logger.info("Manual fetch completed for entity with id {}", id);
        } catch (Exception e) {
            logger.error("Manual fetch failed for entity {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch data from external API: " + e.getMessage());
        }

        return ResponseEntity.ok(entity);
    }

    // 4. Get All Entities
    @GetMapping
    public ResponseEntity<List<Entity>> getAllEntities() {
        logger.info("Retrieving all entities, count: {}", entities.size());
        return ResponseEntity.ok(new ArrayList<>(entities.values()));
    }

    // 5. Delete Single Entity
    @PostMapping("/{id}/delete")
    public ResponseEntity<Map<String, String>> deleteEntity(@PathVariable UUID id) {
        if (entities.remove(id) != null) {
            logger.info("Deleted entity with id {}", id);
            return ResponseEntity.ok(Collections.singletonMap("message", "Entity deleted successfully."));
        } else {
            logger.error("Entity with id {} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
    }

    // 6. Delete All Entities
    @PostMapping("/deleteAll")
    public ResponseEntity<Map<String, String>> deleteAllEntities() {
        entities.clear();
        logger.info("Deleted all entities");
        return ResponseEntity.ok(Collections.singletonMap("message", "All entities deleted successfully."));
    }

    private boolean isApiUrlValid(JsonNode apiUrlNode) {
        if (apiUrlNode == null) return false;
        JsonNode urlNode = apiUrlNode.get("url");
        if (urlNode == null || !urlNode.isTextual()) return false;
        String url = urlNode.asText();
        if (!StringUtils.hasText(url)) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            logger.error("Invalid URI in api_url: {}", url);
            return false;
        }
    }

    /**
     * Fetches data from the entity's apiUrl and updates fetchedData and fetchedAt.
     * This method blocks and should be called asynchronously when needed.
     */
    private void fetchAndUpdateEntityData(Entity entity) {
        try {
            JsonNode urlNode = entity.getApiUrl().get("url");
            if (urlNode == null || !urlNode.isTextual()) {
                logger.error("Invalid api_url format in entity {}", entity.getId());
                return;
            }
            String url = urlNode.asText();

            logger.info("Fetching data from external API for entity {}: {}", entity.getId(), url);

            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                logger.error("Received null response from external API for entity {}", entity.getId());
                return;
            }

            JsonNode fetchedJson = objectMapper.readTree(response);

            entity.setFetchedData(fetchedJson);
            entity.setFetchedAt(Instant.now());

            // Update entity in the map to reflect changes
            entities.put(entity.getId(), entity);

            logger.info("Fetched data updated for entity {}", entity.getId());
        } catch (Exception e) {
            logger.error("Failed to fetch data for entity {}: {}", entity.getId(), e.getMessage());
            // TODO: Consider retry or error status update in a real implementation
        }
    }

    // Minimal global exception handler example for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
```
