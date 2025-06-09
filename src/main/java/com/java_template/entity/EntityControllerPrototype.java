```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/entities")
@Slf4j
public class EntityControllerPrototype {

    private final Map<String, Entity> entityStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Entity {
        private String entityId;
        private String apiUrl;
        private JsonNode fetchedData;
        private String fetchedAt;
    }

    @PostMapping
    public ResponseEntity<Entity> createEntity(@RequestBody Entity entity) {
        log.info("Creating entity with API URL: {}", entity.getApiUrl());
        String entityId = "entity-" + System.currentTimeMillis();
        entity.setEntityId(entityId);

        CompletableFuture.runAsync(() -> fetchData(entityId, entity.getApiUrl()));

        entityStore.put(entityId, entity);
        return new ResponseEntity<>(entity, HttpStatus.CREATED);
    }

    @PostMapping("/{entityId}")
    public ResponseEntity<Entity> updateEntity(@PathVariable String entityId, @RequestBody Entity entity) {
        log.info("Updating entity with ID: {}", entityId);
        Entity existingEntity = entityStore.get(entityId);

        if (existingEntity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        existingEntity.setApiUrl(entity.getApiUrl());
        CompletableFuture.runAsync(() -> fetchData(entityId, entity.getApiUrl()));

        return ResponseEntity.ok(existingEntity);
    }

    @PostMapping("/{entityId}/fetch")
    public ResponseEntity<Entity> fetchData(@PathVariable String entityId) {
        log.info("Manually fetching data for entity ID: {}", entityId);
        Entity entity = entityStore.get(entityId);

        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        fetchData(entityId, entity.getApiUrl());
        return ResponseEntity.ok(entity);
    }

    @GetMapping
    public ResponseEntity<Map<String, Entity>> getAllEntities() {
        log.info("Fetching all entities");
        return ResponseEntity.ok(entityStore);
    }

    @DeleteMapping("/{entityId}")
    public ResponseEntity<Void> deleteEntity(@PathVariable String entityId) {
        log.info("Deleting entity with ID: {}", entityId);
        entityStore.remove(entityId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllEntities() {
        log.info("Deleting all entities");
        entityStore.clear();
        return ResponseEntity.noContent().build();
    }

    private void fetchData(String entityId, String apiUrl) {
        try {
            String response = restTemplate.getForObject(apiUrl, String.class);
            JsonNode fetchedData = objectMapper.readTree(response);

            Entity entity = entityStore.get(entityId);
            if (entity != null) {
                entity.setFetchedData(fetchedData);
                entity.setFetchedAt(String.valueOf(System.currentTimeMillis()));
                log.info("Data fetched successfully for entity ID: {}", entityId);
            }
        } catch (Exception e) {
            log.error("Error fetching data for entity ID: {}", entityId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling error: {}", ex.getStatusCode().toString());
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ex.getStatusCode().toString());
    }
}
```

This prototype sets up a basic Spring Boot controller with mocked storage and asynchronous data fetching using the RestTemplate and ObjectMapper. The controller is equipped with basic logging and error handling. It provides endpoints for creating, updating, fetching, and deleting entities, adhering to the functional requirements specified.