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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/entities")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Entity>> createEntity(@RequestBody @Valid Entity newEntity) {
        return entityService.addItem("entity_name", ENTITY_VERSION, newEntity)
            .thenApply(technicalId -> {
                newEntity.setTechnicalId(technicalId);
                logger.info("Entity created with technical ID: {}", technicalId);
                return ResponseEntity.status(HttpStatus.CREATED).body(newEntity);
            })
            .exceptionally(e -> {
                logger.error("Error creating entity: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            });
    }

    @PostMapping("/{id}")
    public CompletableFuture<ResponseEntity<Entity>> updateEntity(@PathVariable UUID id, @RequestBody @Valid Entity updatedEntity) {
        return entityService.updateItem("entity_name", ENTITY_VERSION, id, updatedEntity)
            .thenApply(technicalId -> {
                updatedEntity.setTechnicalId(technicalId);
                logger.info("Entity updated with technical ID: {}", technicalId);
                return ResponseEntity.ok(updatedEntity);
            })
            .exceptionally(e -> {
                logger.error("Error updating entity: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            });
    }

    @PostMapping("/{id}/fetch")
    public CompletableFuture<ResponseEntity<Entity>> manualFetch(@PathVariable UUID id) {
        return entityService.getItem("entity_name", ENTITY_VERSION, id)
            .thenCompose(entityNode -> {
                if (entityNode == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
                }
                Entity entity = objectMapper.convertValue(entityNode, Entity.class);
                return entityService.updateItem("entity_name", ENTITY_VERSION, id, entity)
                    .thenApply(technicalId -> {
                        entity.setTechnicalId(technicalId);
                        logger.info("Data fetched manually for entity technical ID: {}", technicalId);
                        return ResponseEntity.ok(entity);
                    });
            })
            .exceptionally(e -> {
                logger.error("Error fetching data: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<Void>> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem("entity_name", ENTITY_VERSION, id)
            .thenApply(deletedTechnicalId -> {
                logger.info("Entity deleted with technical ID: {}", deletedTechnicalId);
                return ResponseEntity.noContent().build();
            });
    }

    @DeleteMapping
    public CompletableFuture<ResponseEntity<Void>> deleteAllEntities() {
        return entityService.deleteItems("entity_name", ENTITY_VERSION)
            .thenApply(deletedItemsInfo -> {
                logger.info("All entities deleted");
                return ResponseEntity.noContent().build();
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<JsonNode>> getAllEntities() {
        return entityService.getItems("entity_name", ENTITY_VERSION)
            .thenApply(ResponseEntity::ok);
    }

    private JsonNode fetchDataFromApi(String apiUrl) throws Exception {
        // Mocked API call - replace with actual API call logic
        try {
            // Simulate fetching data
            return objectMapper.readTree("{\"data\":\"sample data\"}");
        } catch (Exception e) {
            logger.error("Failed to fetch data from API: {}", e.getMessage());
            throw new Exception("Failed to fetch data from API", e);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Entity {
        private UUID technicalId;

        @NotBlank
        @Size(max = 255)
        private String apiUrl;

        private JsonNode fetchedData;
        private LocalDateTime fetchedAt;
    }
}