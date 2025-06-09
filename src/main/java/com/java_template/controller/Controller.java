package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/entities")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public CompletableFuture<Entity> createEntity(@RequestBody @Valid EntityRequest request) {
        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("apiUrl", request.getApiUrl());

        return entityService.addItem("entity", ENTITY_VERSION, entity)
                .thenApply(technicalId -> {
                    Entity createdEntity = new Entity();
                    createdEntity.setTechnicalId(technicalId);
                    logger.info("Created entity with technical ID: {}", technicalId);
                    return createdEntity;
                });
    }

    @PostMapping("/{id}")
    public CompletableFuture<Entity> updateEntity(@PathVariable UUID id, @RequestBody @Valid EntityRequest request) {
        return entityService.getItem("entity", ENTITY_VERSION, id)
                .thenCompose(itemNode -> {
                    if (itemNode == null) {
                        logger.error("Entity not found for technical ID: {}", id);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
                    }
                    ObjectNode entity = (ObjectNode) itemNode;
                    entity.put("apiUrl", request.getApiUrl());
                    return entityService.updateItem("entity", ENTITY_VERSION, id, entity)
                            .thenApply(updatedId -> {
                                logger.info("Updated entity with technical ID: {}", updatedId);
                                return new Entity(updatedId, entity.get("apiUrl").asText(), entity.get("fetchedData"), entity.get("fetchedAt").asLong());
                            });
                });
    }

    @PostMapping("/{id}/fetch")
    public CompletableFuture<Entity> fetchEntityData(@PathVariable UUID id) {
        return entityService.getItem("entity", ENTITY_VERSION, id)
                .thenApply(itemNode -> {
                    if (itemNode == null) {
                        logger.error("Entity not found for technical ID: {}", id);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
                    }
                    ObjectNode entity = (ObjectNode) itemNode;
                    logger.info("Fetched data for entity with technical ID: {}", id);
                    return new Entity(id, entity.get("apiUrl").asText(), entity.get("fetchedData"), entity.get("fetchedAt").asLong());
                });
    }

    @GetMapping
    public CompletableFuture<JsonNode> getAllEntities() {
        logger.info("Retrieving all entities");
        return entityService.getItems("entity", ENTITY_VERSION);
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<Void> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem("entity", ENTITY_VERSION, id)
                .thenAccept(deletedId -> {
                    if (deletedId == null) {
                        logger.error("Entity not found for technical ID: {}", id);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
                    }
                    logger.info("Deleted entity with technical ID: {}", deletedId);
                });
    }

    @DeleteMapping
    public CompletableFuture<Void> deleteAllEntities() {
        return entityService.deleteItems("entity", ENTITY_VERSION)
                .thenAccept(deletedItemsInfo -> logger.info("Deleted all entities"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleException(ResponseStatusException ex) {
        return Map.of("error", ex.getStatusCode().toString());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Entity {
        private UUID technicalId;
        private String apiUrl;
        private JsonNode fetchedData;
        private Long fetchedAt;
    }

    @Data
    static class EntityRequest {
        @NotBlank
        @Size(max = 255)
        private String apiUrl;
    }
}
