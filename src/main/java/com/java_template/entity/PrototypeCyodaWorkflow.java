package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/prototype/entities")
@Slf4j
@Validated
public class CyodaEntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Entity>> createEntity(@RequestBody @Valid EntityRequest request) {
        Entity entity = new Entity(null, request.getApiUrl(), null, null);

        return entityService.addItem("Entity", ENTITY_VERSION, entity, this::processEntity)
                .thenApply(technicalId -> {
                    entity.setTechnicalId(technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(entity);
                });
    }

    private CompletableFuture<Entity> processEntity(Entity entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = entity.getApiUrl();
                String response = restTemplate.getForObject(apiUrl, String.class);
                JsonNode fetchedData = objectMapper.readTree(response);
                entity.setFetchedData(fetchedData);
                entity.setFetchedAt(LocalDateTime.now());
                log.info("Data fetched successfully for entity: {}", entity.getTechnicalId());
            } catch (Exception e) {
                log.error("Failed to fetch data for entity: {}. Error: {}", entity.getTechnicalId(), e.getMessage());
                // Handle exception if needed
            }
            return entity;
        });
    }

    @PostMapping("/{id}")
    public CompletableFuture<ResponseEntity<Entity>> updateEntity(@PathVariable UUID id, @RequestBody @Valid EntityRequest request) {
        return entityService.getItem("Entity", ENTITY_VERSION, id)
                .thenCompose(item -> {
                    Entity entity = objectMapper.convertValue(item, Entity.class);
                    entity.setApiUrl(request.getApiUrl());

                    return entityService.updateItem("Entity", ENTITY_VERSION, id, entity)
                            .thenApply(updatedId -> ResponseEntity.ok(entity));
                });
    }

    @PostMapping("/{id}/fetch")
    public CompletableFuture<ResponseEntity<Entity>> manualFetch(@PathVariable UUID id) {
        return entityService.getItem("Entity", ENTITY_VERSION, id)
                .thenApply(item -> {
                    Entity entity = objectMapper.convertValue(item, Entity.class);
                    fetchDataFromApi(entity);
                    return ResponseEntity.ok(entity);
                });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<Entity>>> getAllEntities() {
        return entityService.getItems("Entity", ENTITY_VERSION)
                .thenApply(items -> {
                    List<Entity> entities = objectMapper.convertValue(items, objectMapper.getTypeFactory().constructCollectionType(List.class, Entity.class));
                    return ResponseEntity.ok(entities);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<String>> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem("Entity", ENTITY_VERSION, id)
                .thenApply(deletedId -> ResponseEntity.ok("Entity deleted successfully."));
    }

    @DeleteMapping
    public CompletableFuture<ResponseEntity<String>> deleteAllEntities() {
        return entityService.deleteItems("Entity", ENTITY_VERSION)
                .thenApply(deletedItemsInfo -> ResponseEntity.ok("All entities deleted successfully."));
    }

    private void fetchDataFromApi(Entity entity) {
        CompletableFuture.runAsync(() -> {
            try {
                String apiUrl = entity.getApiUrl();
                String response = restTemplate.getForObject(apiUrl, String.class);
                JsonNode fetchedData = objectMapper.readTree(response);
                entity.setFetchedData(fetchedData);
                entity.setFetchedAt(LocalDateTime.now());
                log.info("Data fetched successfully for entity technicalId: {}", entity.getTechnicalId());
            } catch (Exception e) {
                log.error("Failed to fetch data for entity technicalId: {}. Error: {}", entity.getTechnicalId(), e.getMessage());
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
        private UUID technicalId;
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