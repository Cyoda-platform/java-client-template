To update the code as per your requirements, we need to add a new workflow function named `processEntity` and pass it as an argument to the `entityService.addItem` method. This workflow function will be applied to the entity before it is persisted. Here's the updated Java code:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        Entity entity = new Entity(null, request.getApiUrl(), null, null);
        return entityService.addItem("entity", ENTITY_VERSION, entity, this::processEntity)
                .thenApply(technicalId -> {
                    entity.setTechnicalId(technicalId);
                    fetchDataAsync(entity);
                    logger.info("Created entity with technical ID: {}", technicalId);
                    return entity;
                });
    }

    private Entity processEntity(Entity entity) {
        // Example processing logic
        // Modify the entity as needed before it is persisted
        entity.setApiUrl(entity.getApiUrl().toLowerCase()); // Example modification
        logger.info("Processed entity with apiUrl: {}", entity.getApiUrl());
        return entity;
    }

    @PostMapping("/{id}")
    public CompletableFuture<Entity> updateEntity(@PathVariable UUID id, @RequestBody @Valid EntityRequest request) {
        return entityService.getItem("entity", ENTITY_VERSION, id)
                .thenCompose(itemNode -> {
                    if (itemNode == null) {
                        logger.error("Entity not found for technical ID: {}", id);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
                    }
                    Entity entity = objectMapper.convertValue(itemNode, Entity.class);
                    entity.setApiUrl(request.getApiUrl());
                    return entityService.updateItem("entity", ENTITY_VERSION, id, entity)
                            .thenApply(updatedId -> {
                                fetchDataAsync(entity);
                                logger.info("Updated entity with technical ID: {}", updatedId);
                                return entity;
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
                    Entity entity = objectMapper.convertValue(itemNode, Entity.class);
                    fetchDataAsync(entity);
                    logger.info("Manually fetched data for entity technical ID: {}", id);
                    return entity;
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

    private void fetchDataAsync(Entity entity) {
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Replace with actual HTTP client call
                JsonNode fetchedData = objectMapper.readTree("{\"example\": \"data\"}");
                entity.setFetchedData(fetchedData);
                entity.setFetchedAt(System.currentTimeMillis());
                logger.info("Fetched data for entity with technical ID: {}", entity.getTechnicalId());
            } catch (Exception e) {
                logger.error("Error fetching data for entity with technical ID: {}", entity.getTechnicalId(), e);
            }
        });
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
```

### Explanation:

1. **Workflow Function Implementation**: The `processEntity` method is created to process the entity before it is persisted. This function modifies the `apiUrl` by converting it to lowercase as an example. You can customize this function to perform other necessary transformations.

2. **Passing the Workflow Function**: The `processEntity` function is passed as an argument to the `entityService.addItem` method.

3. **Logging**: Added a log statement within the `processEntity` method to log the changes made to the entity.

Feel free to modify the `processEntity` method to suit your specific business logic needs.