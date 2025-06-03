```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/entities")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // In-memory store (mock persistence)
    private final Map<UUID, Entity> entityStore = new ConcurrentHashMap<>();

    // ========================
    // === Entity Model Class ==
    // ========================
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
    @AllArgsConstructor
    public static class ApiUrlRequest {
        private JsonNode api_url;
    }

    // ========================
    // === Create Entity =======
    // ========================
    @PostMapping
    public Entity createEntity(@RequestBody ApiUrlRequest request) {
        logger.info("Create entity request received.");
        if (request == null || request.getApi_url() == null || request.getApi_url().isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url is required");
        }

        UUID id = UUID.randomUUID();
        Entity entity = new Entity(id, request.getApi_url(), null, null);
        entityStore.put(id, entity);
        logger.info("Entity {} created with api_url: {}", id, entity.getApiUrl());

        // Fire-and-forget fetch data asynchronously
        fetchDataAndUpdateEntityAsync(id, entity.getApiUrl());

        return entity;
    }

    // ========================
    // === Update Entity =======
    // ========================
    @PostMapping("/{id}")
    public Entity updateEntity(@PathVariable UUID id, @RequestBody ApiUrlRequest request) {
        logger.info("Update entity {} request received.", id);
        Entity entity = entityStore.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        if (request == null || request.getApi_url() == null || request.getApi_url().isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url is required");
        }

        entity.setApiUrl(request.getApi_url());
        // Reset fetched data & timestamp on update, will be updated asynchronously
        entity.setFetchedData(null);
        entity.setFetchedAt(null);

        logger.info("Entity {} api_url updated to: {}", id, entity.getApiUrl());

        // Fire-and-forget fetch data asynchronously
        fetchDataAndUpdateEntityAsync(id, entity.getApiUrl());

        return entity;
    }

    // ========================
    // === Manual Fetch ========
    // ========================
    @PostMapping("/{id}/fetch")
    public Entity manualFetch(@PathVariable UUID id) {
        logger.info("Manual fetch requested for entity {}", id);
        Entity entity = entityStore.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        if (entity.getApiUrl() == null || entity.getApiUrl().isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entity has no valid api_url");
        }

        // Synchronously fetch data before returning
        try {
            JsonNode fetchedData = fetchFromExternalApi(entity.getApiUrl());
            entity.setFetchedData(fetchedData);
            entity.setFetchedAt(Instant.now());
            logger.info("Manual fetch success for entity {}", id);
        } catch (Exception e) {
            logger.error("Manual fetch failed for entity {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch external data");
        }

        return entity;
    }

    // ========================
    // === Get All Entities ====
    // ========================
    @GetMapping
    public Collection<Entity> getAllEntities() {
        logger.info("Get all entities request received.");
        return entityStore.values();
    }

    // ========================
    // === Delete Single =======
    // ========================
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntity(@PathVariable UUID id) {
        logger.info("Delete entity {} request received.", id);
        if (entityStore.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        logger.info("Entity {} deleted.", id);
    }

    // ========================
    // === Delete All ==========
    // ========================
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllEntities() {
        logger.info("Delete all entities request received.");
        entityStore.clear();
        logger.info("All entities deleted.");
    }

    // ========================
    // === Async fetch helper ==
    // ========================

    @Async
    public void fetchDataAndUpdateEntityAsync(UUID id, JsonNode apiUrlNode) {
        // TODO: Fire-and-forget async fetch, no retries, no backpressure (prototype)
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Async fetch started for entity {}", id);
                JsonNode fetchedData = fetchFromExternalApi(apiUrlNode);
                Entity entity = entityStore.get(id);
                if (entity != null) {
                    entity.setFetchedData(fetchedData);
                    entity.setFetchedAt(Instant.now());
                    logger.info("Async fetch success for entity {}", id);
                } else {
                    logger.warn("Entity {} not found during async fetch update", id);
                }
            } catch (Exception e) {
                logger.error("Async fetch failed for entity {}: {}", id, e.getMessage());
            }
        });
    }

    // ========================
    // === External API fetch ==
    // ========================
    private JsonNode fetchFromExternalApi(JsonNode apiUrlNode) throws Exception {
        if (!apiUrlNode.isTextual()) {
            throw new IllegalArgumentException("api_url must be a JSON string");
        }
        String apiUrl = apiUrlNode.asText();
        logger.info("Fetching external data from URL: {}", apiUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            logger.error("External API responded with status {}", statusCode);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External API error: " + statusCode);
        }

        String body = response.body();
        return objectMapper.readTree(body);
    }

    // ========================
    // === Exception Handlers ==
    // ========================
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("status", 500);
        error.put("error", "Internal server error");
        return error;
    }
}
```
