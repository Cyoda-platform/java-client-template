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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/entities")
@Slf4j
@Validated
public class CyodaEntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Entity {
        private String technicalId;

        @NotBlank(message = "API URL must not be blank")
        private String apiUrl;

        private JsonNode fetchedData;
        private String fetchedAt;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Entity>> createEntity(@RequestBody @Valid Entity entity) {
        log.info("Creating entity with API URL: {}", entity.getApiUrl());
        return entityService.addItem("Entity", ENTITY_VERSION, objectMapper.convertValue(entity, ObjectNode.class), this::processEntity)
                .thenApply(technicalId -> {
                    entity.setTechnicalId(technicalId.toString());
                    return new ResponseEntity<>(entity, HttpStatus.CREATED);
                });
    }

    @PostMapping("/{entityId}")
    public CompletableFuture<ResponseEntity<Entity>> updateEntity(@PathVariable String entityId, @RequestBody @Valid Entity entity) {
        log.info("Updating entity with technical ID: {}", entityId);
        return entityService.updateItem("Entity", ENTITY_VERSION, UUID.fromString(entityId), objectMapper.convertValue(entity, ObjectNode.class))
                .thenApply(technicalId -> ResponseEntity.ok(entity));
    }

    @GetMapping("/{entityId}/fetch")
    public CompletableFuture<ResponseEntity<Entity>> fetchData(@PathVariable String entityId) {
        log.info("Manually fetching data for entity technical ID: {}", entityId);
        return entityService.getItem("Entity", ENTITY_VERSION, UUID.fromString(entityId))
                .thenApply(item -> {
                    Entity entity = objectMapper.convertValue(item, Entity.class);
                    fetchData(entity.getTechnicalId(), entity.getApiUrl());
                    return ResponseEntity.ok(entity);
                });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<Entity>>> getAllEntities() {
        log.info("Fetching all entities");
        return entityService.getItems("Entity", ENTITY_VERSION)
                .thenApply(items -> ResponseEntity.ok(objectMapper.convertValue(items, List.class)));
    }

    @DeleteMapping("/{entityId}")
    public CompletableFuture<ResponseEntity<Void>> deleteEntity(@PathVariable String entityId) {
        log.info("Deleting entity with technical ID: {}", entityId);
        return entityService.deleteItem("Entity", ENTITY_VERSION, UUID.fromString(entityId))
                .thenApply(deletedId -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    public CompletableFuture<ResponseEntity<Void>> deleteAllEntities() {
        log.info("Deleting all entities");
        return entityService.deleteItems("Entity", ENTITY_VERSION)
                .thenApply(deletedItemsInfo -> ResponseEntity.noContent().build());
    }

    private void fetchData(String entityId, String apiUrl) {
        try {
            String response = new RestTemplate().getForObject(apiUrl, String.class);
            JsonNode fetchedData = objectMapper.readTree(response);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", entityId));
            entityService.getItemsByCondition("Entity", ENTITY_VERSION, condition)
                    .thenApply(items -> {
                        if (items.size() > 0) {
                            ObjectNode item = (ObjectNode) items.get(0);
                            item.set("fetchedData", fetchedData);
                            item.put("fetchedAt", String.valueOf(System.currentTimeMillis()));
                            entityService.updateItem("Entity", ENTITY_VERSION, UUID.fromString(entityId), item);
                        }
                        return null;
                    });
            log.info("Data fetched successfully for entity technical ID: {}", entityId);
        } catch (Exception e) {
            log.error("Error fetching data for entity technical ID: {}", entityId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String apiUrl = entity.get("apiUrl").asText();
            log.info("Processing entity with API URL: {}", apiUrl);

            try {
                String response = new RestTemplate().getForObject(apiUrl, String.class);
                JsonNode fetchedData = objectMapper.readTree(response);

                entity.set("fetchedData", fetchedData);
                entity.put("fetchedAt", String.valueOf(System.currentTimeMillis()));

            } catch (Exception e) {
                log.error("Error processing entity with API URL: {}", apiUrl, e);
            }

            return entity;
        });
    }
}