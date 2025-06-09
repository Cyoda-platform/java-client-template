package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/entities")
@Slf4j
@Validated
public class EntityControllerPrototype {

    private final Map<String, Entity> entities = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid EntityRequest request) {
        String id = UUID.randomUUID().toString();
        Entity entity = new Entity(id, request.getApiUrl(), null, null);
        entities.put(id, entity);

        // Trigger data fetching asynchronously
        fetchDataFromApi(entity);

        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PostMapping("/{id}")
    public ResponseEntity<Entity> updateEntity(@PathVariable String id, @RequestBody @Valid EntityRequest request) {
        Entity entity = entities.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        entity.setApiUrl(request.getApiUrl());

        // Trigger data fetching asynchronously
        fetchDataFromApi(entity);

        return ResponseEntity.ok(entity);
    }

    @PostMapping("/{id}/fetch")
    public ResponseEntity<Entity> manualFetch(@PathVariable String id) {
        Entity entity = entities.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        // Trigger data fetching asynchronously
        fetchDataFromApi(entity);

        return ResponseEntity.ok(entity);
    }

    @GetMapping
    public ResponseEntity<Map<String, Entity>> getAllEntities() {
        return ResponseEntity.ok(entities);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEntity(@PathVariable String id) {
        if (entities.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        return ResponseEntity.ok("Entity deleted successfully.");
    }

    @DeleteMapping
    public ResponseEntity<String> deleteAllEntities() {
        entities.clear();
        return ResponseEntity.ok("All entities deleted successfully.");
    }

    private void fetchDataFromApi(Entity entity) {
        CompletableFuture.runAsync(() -> {
            try {
                String apiUrl = entity.getApiUrl();
                String response = restTemplate.getForObject(apiUrl, String.class);
                JsonNode fetchedData = objectMapper.readTree(response);
                entity.setFetchedData(fetchedData);
                entity.setFetchedAt(LocalDateTime.now());
                log.info("Data fetched successfully for entity id: {}", entity.getId());
            } catch (Exception e) {
                log.error("Failed to fetch data for entity id: {}. Error: {}", entity.getId(), e.getMessage());
            }
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Entity {
        private String id;
        private String apiUrl;
        private JsonNode fetchedData;
        private LocalDateTime fetchedAt;
    }

    @Data
    static class EntityRequest {
        @NotBlank
        private String apiUrl;
    }
}