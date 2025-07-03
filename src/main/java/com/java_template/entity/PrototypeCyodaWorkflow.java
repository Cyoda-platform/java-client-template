```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function to process the "entityModel" entity before persistence.
     * This function receives the entity data, can modify it asynchronously, and returns it.
     *
     * Remember: Cannot add/update/delete entities of the same entityModel inside this function to avoid infinite recursion.
     */
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processEntityModel = entityData -> {
        // Example process: Here you can modify entityData if needed asynchronously.
        // For demonstration, just return the same entityData wrapped in a completed future.
        return CompletableFuture.completedFuture(entityData);
    };

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE) // must be first
    public ResponseEntity<IdResponse> postItem(
            @RequestBody @NotNull ObjectNode data
    ) {
        logger.info("Received POST /cyoda/items request");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "entityModel", // replace with actual entity name if known
                ENTITY_VERSION,
                data,
                processEntityModel
        );

        UUID technicalId = idFuture.join();
        logger.info("Item stored with technicalId {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(technicalId.toString()));
    }

    @GetMapping("/{id}") // must be first
    public ResponseEntity<ObjectNode> getItem(
            @PathVariable("id")
            @Pattern(regexp = "[0-9a-fA-F\\-]{36}", message = "Invalid ID format") String id
    ) {
        logger.info("Received GET /cyoda/items/{} request", id);
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "entityModel", // replace with actual entity name if known
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode item = itemFuture.join();
        if (item == null || !item.has("technicalId")) {
            logger.error("Item not found for technicalId {}", technicalId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        logger.info("Item found for technicalId {}", technicalId);
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getStatusCode().toString(),
                ex.getReason()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class IdResponse {
        private String id;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }
}
```
---

### Explanation:

- Added a new workflow function `processEntityModel` with the required naming prefix `process` + `{entity_name}` (here entity name is `"entityModel"`).
- This function takes `ObjectNode` (entity data), returns `CompletableFuture<ObjectNode>`.
- Passed this workflow function as the last argument to `entityService.addItem`.
- Kept the existing logic unchanged except this addition.
- Used `Function<ObjectNode, CompletableFuture<ObjectNode>>` as the workflow function type, matching the async processing described.