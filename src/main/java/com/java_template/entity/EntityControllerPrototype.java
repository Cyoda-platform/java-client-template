```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.IOException;
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

    // In-memory store simulating persistence
    private final Map<UUID, Entity> entityStore = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Entity {
        private UUID id;
        private JsonNode apiUrl;
        private JsonNode fetchedData;
        private Instant fetchedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CreateOrUpdateRequest {
        private JsonNode api_url;
    }

    // ------------------- Create Entity -------------------
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Entity createEntity(@RequestBody CreateOrUpdateRequest req) {
        logger.info("Received request to create entity with api_url: {}", req.getApi_url());
        if (req.getApi_url() == null || !req.getApi_url().isTextual()) {
            logger.error("Invalid or missing api_url");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url must be a JSON string");
        }

        UUID id = UUID.randomUUID();
        Entity entity = new Entity();
        entity.setId(id);
        entity.setApiUrl(req.getApi_url());

        // Persist entity first with empty fetched data/timestamp
        entity.setFetchedData(null);
        entity.setFetchedAt(null);
        entityStore.put(id, entity);

        try {
            fetchDataAndUpdateEntity(id, entity.getApiUrl());
        } catch (Exception e) {
            logger.error("Failed to fetch data on creation for entity {}: {}", id, e.getMessage());
            // We do not remove entity; user can retry manually
        }

        return entityStore.get(id);
    }

    // ------------------- Update Entity API URL and Fetch -------------------
    @PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Entity updateEntityApiUrl(@PathVariable UUID id, @RequestBody CreateOrUpdateRequest req) {
        logger.info("Received request to update entity {} with api_url: {}", id, req.getApi_url());

        Entity entity = entityStore.get(id);
        if (entity == null) {
            logger.error("Entity {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        if (req.getApi_url() == null || !req.getApi_url().isTextual()) {
            logger.error("Invalid or missing api_url");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url must be a JSON string");
        }

        entity.setApiUrl(req.getApi_url());

        try {
            fetchDataAndUpdateEntity(id, entity.getApiUrl());
        } catch (Exception e) {
            logger.error("Failed to fetch data on update for entity {}: {}", id, e.getMessage());
            // Keep entity updated with new URL even if fetch fails
        }

        return entityStore.get(id);
    }

    // ------------------- Manual Fetch Data Trigger -------------------
    @PostMapping(value = "/{id}/fetch", produces = MediaType.APPLICATION_JSON_VALUE)
    public Entity manualFetch(@PathVariable UUID id) {
        logger.info("Received manual fetch request for entity {}", id);
        Entity entity = entityStore.get(id);
        if (entity == null) {
            logger.error("Entity {} not found for manual fetch", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        if (entity.getApiUrl() == null || !entity.getApiUrl().isTextual()) {
            logger.error("Entity {} api_url is invalid", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stored api_url is invalid or missing");
        }

        try {
            fetchDataAndUpdateEntity(id, entity.getApiUrl());
        } catch (Exception e) {
            logger.error("Failed to fetch data manually for entity {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch external data: " + e.getMessage());
        }
        return entityStore.get(id);
    }

    // ------------------- Get All Entities -------------------
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Entity> getAllEntities() {
        logger.info("Received request to get all entities");
        return entityStore.values();
    }

    // ------------------- Delete Single Entity -------------------
    @DeleteMapping("/{id}")
    public Map<String, String> deleteEntity(@PathVariable UUID id) {
        logger.info("Received request to delete entity {}", id);
        Entity removed = entityStore.remove(id);
        if (removed == null) {
            logger.error("Entity {} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        return Map.of("message", "Entity " + id + " deleted successfully.");
    }

    // ------------------- Delete All Entities -------------------
    @DeleteMapping
    public Map<String, String> deleteAllEntities() {
        logger.info("Received request to delete all entities");
        entityStore.clear();
        return Map.of("message", "All entities deleted successfully.");
    }

    // ------------------- Helper Method: Fetch external data and update entity -------------------
    private void fetchDataAndUpdateEntity(UUID id, JsonNode apiUrlJson) throws IOException, InterruptedException {
        String apiUrl = apiUrlJson.asText();
        logger.info("Fetching data from external API for entity {}: {}", id, apiUrl);

        // Basic URL validation TODO: Enhance if needed
        if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
            logger.error("api_url {} is not a valid HTTP/HTTPS URL", apiUrl);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url must be a valid HTTP/HTTPS URL");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            String body = response.body();
            JsonNode fetchedData = objectMapper.readTree(body);
            Entity entity = entityStore.get(id);
            if (entity != null) {
                entity.setFetchedData(fetchedData);
                entity.setFetchedAt(Instant.now());
                logger.info("Updated entity {} with fetched data and timestamp", id);
            } else {
                logger.error("Entity {} disappeared during fetch update", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found during update");
            }
        } else {
            logger.error("External API returned status {} for entity {}", status, id);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External API responded with status " + status);
        }
    }

    // ------------------- Basic Error Handler -------------------
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getReason()
        );
    }
}
```
