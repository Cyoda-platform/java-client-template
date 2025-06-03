package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/entities")
@Validated
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
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
    public static class ApiUrlRequestDto {
        @NotBlank
        @Pattern(regexp = "^https?://.+", message = "api_url must be a valid HTTP/HTTPS URL")
        private String api_url;
    }

    @PostMapping
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid ApiUrlRequestDto request) {
        UUID id = UUID.randomUUID();
        JsonNode apiUrlNode = objectMapper.createObjectNode().put("url", request.getApi_url());
        Entity entity = new Entity(id, apiUrlNode, null, null);
        entities.put(id, entity);
        logger.info("Created entity with id {}", id);
        CompletableFuture.runAsync(() -> fetchAndUpdateEntityData(entity))
                .exceptionally(ex -> {
                    logger.error("Error fetching data on entity creation: {}", ex.getMessage());
                    return null;
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PostMapping("/{id}")
    public ResponseEntity<Entity> updateEntityApiUrl(@PathVariable UUID id, @RequestBody @Valid ApiUrlRequestDto request) {
        Entity entity = entities.get(id);
        if (entity == null) {
            logger.error("Entity with id {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        JsonNode apiUrlNode = objectMapper.createObjectNode().put("url", request.getApi_url());
        entity.setApiUrl(apiUrlNode);
        entities.put(id, entity);
        logger.info("Updated api_url for entity with id {}", id);
        CompletableFuture.runAsync(() -> fetchAndUpdateEntityData(entity))
                .exceptionally(ex -> {
                    logger.error("Error fetching data on entity update: {}", ex.getMessage());
                    return null;
                });
        return ResponseEntity.ok(entity);
    }

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

    @GetMapping
    public ResponseEntity<List<Entity>> getAllEntities() {
        logger.info("Retrieving all entities, count: {}", entities.size());
        return ResponseEntity.ok(new ArrayList<>(entities.values()));
    }

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

    @PostMapping("/deleteAll")
    public ResponseEntity<Map<String, String>> deleteAllEntities() {
        entities.clear();
        logger.info("Deleted all entities");
        return ResponseEntity.ok(Collections.singletonMap("message", "All entities deleted successfully."));
    }

    private void fetchAndUpdateEntityData(Entity entity) {
        JsonNode urlNode = entity.getApiUrl().get("url");
        if (urlNode == null || !urlNode.isTextual()) {
            logger.error("Invalid api_url format in entity {}", entity.getId());
            return;
        }
        String url = urlNode.asText();
        try {
            new URI(url); // validate URI
            logger.info("Fetching data from external API for entity {}: {}", entity.getId(), url);
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                logger.error("Received null response from external API for entity {}", entity.getId());
                return;
            }
            JsonNode fetchedJson = objectMapper.readTree(response);
            entity.setFetchedData(fetchedJson);
            entity.setFetchedAt(Instant.now());
            entities.put(entity.getId(), entity);
            logger.info("Fetched data updated for entity {}", entity.getId());
        } catch (Exception e) {
            logger.error("Failed to fetch data for entity {}: {}", entity.getId(), e.getMessage());
            // TODO: Consider retry or error status update in a real implementation
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Collections.singletonMap("error", ex.getReason()));
    }
}