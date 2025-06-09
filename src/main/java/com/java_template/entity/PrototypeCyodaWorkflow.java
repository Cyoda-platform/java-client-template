package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import static com.java_template.common.config.Config.ENTITY_VERSION;

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
    public CompletableFuture<ResponseEntity<ObjectNode>> createEntity(@RequestBody @Valid EntityRequest request) {
        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("apiUrl", request.getApiUrl());

        return entityService.addItem("Entity", ENTITY_VERSION, entity, this::processEntity)
                .thenApply(technicalId -> {
                    entity.put("technicalId", technicalId.toString());
                    return ResponseEntity.status(HttpStatus.CREATED).body(entity);
                });
    }

    private CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = entity.get("apiUrl").asText();
                String response = restTemplate.getForObject(apiUrl, String.class);
                JsonNode fetchedData = objectMapper.readTree(response);
                entity.set("fetchedData", fetchedData);
                entity.put("fetchedAt", LocalDateTime.now().toString());
                log.info("Data fetched successfully for entity: {}", entity);
            } catch (Exception e) {
                log.error("Failed to fetch data for entity. Error: {}", e.getMessage());
                entity.put("fetchError", "Failed to fetch data");
            }
            return entity;
        });
    }

    @PostMapping("/{id}")
    public CompletableFuture<ResponseEntity<ObjectNode>> updateEntity(@PathVariable UUID id, @RequestBody @Valid EntityRequest request) {
        return entityService.getItem("Entity", ENTITY_VERSION, id)
                .thenCompose(item -> {
                    ObjectNode entity = (ObjectNode) item;
                    entity.put("apiUrl", request.getApiUrl());

                    return entityService.updateItem("Entity", ENTITY_VERSION, id, entity)
                            .thenApply(updatedId -> ResponseEntity.ok(entity));
                })
                .exceptionally(ex -> {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
                });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<ObjectNode>>> getAllEntities() {
        return entityService.getItems("Entity", ENTITY_VERSION)
                .thenApply(items -> {
                    List<ObjectNode> entities = objectMapper.convertValue(items, objectMapper.getTypeFactory().constructCollectionType(List.class, ObjectNode.class));
                    return ResponseEntity.ok(entities);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<String>> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem("Entity", ENTITY_VERSION, id)
                .thenApply(deletedId -> ResponseEntity.ok("Entity deleted successfully."))
                .exceptionally(ex -> {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
                });
    }

    @DeleteMapping
    public CompletableFuture<ResponseEntity<String>> deleteAllEntities() {
        return entityService.deleteItems("Entity", ENTITY_VERSION)
                .thenApply(deletedItemsInfo -> ResponseEntity.ok("All entities deleted successfully."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString()));
    }

    static class EntityRequest {
        @NotBlank
        private String apiUrl;

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }
    }
}