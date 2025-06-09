package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/entities")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Entity> entityStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid Entity newEntity) {
        String entityId = generateEntityId();
        try {
            JsonNode fetchedData = fetchDataFromApi(newEntity.getApiUrl());
            newEntity.setFetchedData(fetchedData);
            newEntity.setFetchedAt(LocalDateTime.now());
            entityStore.put(entityId, newEntity);
            logger.info("Entity created with ID: {}", entityId);
            return ResponseEntity.status(HttpStatus.CREATED).body(newEntity);
        } catch (Exception e) {
            logger.error("Error creating entity: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/{id}")
    public ResponseEntity<Entity> updateEntity(@PathVariable String id, @RequestBody @Valid Entity updatedEntity) {
        Entity existingEntity = entityStore.get(id);
        if (existingEntity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        try {
            JsonNode fetchedData = fetchDataFromApi(updatedEntity.getApiUrl());
            existingEntity.setApiUrl(updatedEntity.getApiUrl());
            existingEntity.setFetchedData(fetchedData);
            existingEntity.setFetchedAt(LocalDateTime.now());
            logger.info("Entity updated with ID: {}", id);
            return ResponseEntity.ok(existingEntity);
        } catch (Exception e) {
            logger.error("Error updating entity: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/{id}/fetch")
    public ResponseEntity<Entity> manualFetch(@PathVariable String id) {
        Entity entity = entityStore.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        try {
            JsonNode fetchedData = fetchDataFromApi(entity.getApiUrl());
            entity.setFetchedData(fetchedData);
            entity.setFetchedAt(LocalDateTime.now());
            logger.info("Data fetched manually for entity ID: {}", id);
            return ResponseEntity.ok(entity);
        } catch (Exception e) {
            logger.error("Error fetching data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable String id) {
        if (entityStore.remove(id) != null) {
            logger.info("Entity deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllEntities() {
        entityStore.clear();
        logger.info("All entities deleted");
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Entity>> getAllEntities() {
        return ResponseEntity.ok(entityStore);
    }

    private JsonNode fetchDataFromApi(String apiUrl) throws Exception {
        try {
            String response = restTemplate.getForObject(apiUrl, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Failed to fetch data from API: {}", e.getMessage());
            throw new Exception("Failed to fetch data from API");
        }
    }

    private String generateEntityId() {
        // TODO: Replace with a more robust ID generation mechanism
        return "entity-" + System.currentTimeMillis();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Entity {
        @NotBlank
        @Size(max = 255)
        private String apiUrl;

        private JsonNode fetchedData;
        private LocalDateTime fetchedAt;
    }
}