To adhere to the requirement of moving asynchronous logic to the workflow function and to make the controller more robust and clean, we can refactor the code by shifting the logic of fetching and processing the data to the `processEntity` function. This not only simplifies the controller but also leverages the asynchronous nature of the workflow function.

Let's update the code accordingly:

```java
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
        return entityService.addItem("entity_name", ENTITY_VERSION, newEntity, this::processEntity)
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
        return entityService.updateItem("entity_name", ENTITY_VERSION, id, updatedEntity, this::processEntity)
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
                return entityService.updateItem("entity_name", ENTITY_VERSION, id, entity, this::processEntity)
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

    private CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = entity.get("apiUrl").asText();
                JsonNode fetchedData = fetchDataFromApi(apiUrl);
                entity.set("fetchedData", fetchedData);
                entity.put("fetchedAt", LocalDateTime.now().toString());
                logger.info("Processing entity before persistence: {}", entity);
            } catch (Exception e) {
                logger.error("Failed to process entity: {}", e.getMessage());
                throw new RuntimeException("Failed to process entity", e);
            }
            return entity;
        });
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
```

### Key Changes:

1. **Moved Asynchronous Logic to `processEntity`:** 
   - The `processEntity` method is now responsible for fetching data from the API and updating the entity's state. This method is asynchronous and returns a `CompletableFuture<ObjectNode>`.
   - This method takes an `ObjectNode` (representing the entity) and performs the necessary modifications, including fetching data from an external API.

2. **Simplified Controller Methods:**
   - The controller endpoints (`createEntity`, `updateEntity`, `manualFetch`) are now cleaner, with the asynchronous data fetching and processing logic moved to the workflow function.

3. **Error Handling:**
   - Used `exceptionally` for error handling in asynchronous operations.

This approach not only makes the controllers less cluttered but also ensures that all processing logic related to entity transformation and data fetching is encapsulated within the workflow function.