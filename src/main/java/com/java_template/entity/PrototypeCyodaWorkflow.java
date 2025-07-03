package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.MethodArgumentNotValidException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda/entity/prototype/items")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    /**
     * Workflow function applied asynchronously before persisting the entity.
     * All async tasks and entity state mutations moved here.
     * This example adds timestamp, fetches supplementary "metadata" entity,
     * enriches entity with metadata if available, and performs fire-and-forget async notification.
     */
    private CompletableFuture<ObjectNode> processPrototype(ObjectNode entity) {
        // Add processed timestamp
        entity.put("processedTimestamp", Instant.now().toString());

        // Defensive: Ensure entity has an id for potential async operations
        UUID entityId = null;
        try {
            if (entity.hasNonNull("id")) {
                entityId = UUID.fromString(entity.get("id").asText());
            }
        } catch (IllegalArgumentException ignored) {
            // No valid UUID id in entity, ignore
        }

        // Fetch supplementary entity "metadata" (different model) asynchronously
        CompletableFuture<ObjectNode> metadataFuture = entityService.getItem("metadata", ENTITY_VERSION, UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .exceptionally(ex -> {
                    logger.warn("Failed to fetch metadata entity in workflow", ex);
                    return null;
                });

        // Fire-and-forget async task example: send notification (simulate)
        CompletableFuture<Void> notificationFuture = CompletableFuture.runAsync(() -> {
            try {
                // Simulated async notification logic
                logger.info("Sending notification for prototype entity asynchronously");
                // For example, call external service or message queue here
            } catch (Exception e) {
                logger.error("Error during async notification in workflow", e);
            }
        });

        // Compose all async operations
        return metadataFuture.thenCompose(metadata -> {
            if (metadata != null && !metadata.isEmpty(null)) {
                entity.set("metadata", metadata);
            }
            // Wait for notification fire-and-forget to complete but do not block persistence on it
            return notificationFuture.handle((v, ex) -> {
                if (ex != null) {
                    logger.error("Notification future completed exceptionally", ex);
                }
                return entity; // return mutated entity for persistence
            });
        });
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public CompletableFuture<ResponseEntity<IdResponse>> storeItem(@RequestBody @Valid ItemRequest request) {
        ObjectNode entity;
        try {
            entity = (ObjectNode) entityService.getObjectMapper().readTree(request.getRawJson());
        } catch (Exception e) {
            logger.error("Invalid JSON provided", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format");
        }

        // Pass workflow function to addItem; all async logic moved inside processPrototype
        return entityService.addItem(
                        "prototype",
                        ENTITY_VERSION,
                        entity,
                        this::processPrototype
                )
                .thenApply(id -> {
                    logger.info("Stored item with technicalId: {}", id);
                    return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(id.toString()));
                })
                .exceptionally(ex -> {
                    logger.error("Failed to store item", ex);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store item");
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<ObjectNode>> getItemById(@PathVariable @NotBlank String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
        return entityService.getItem("prototype", ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty(null)) {
                        logger.warn("Item not found for technicalId: {}", id);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item with id " + id + " not found");
                    }
                    logger.info("Retrieved item with technicalId: {}", id);
                    return ResponseEntity.ok(item);
                })
                .exceptionally(ex -> {
                    logger.error("Error retrieving item by id", ex);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve item");
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(
                ex.getStatusCode().toString(),
                ex.getReason(),
                Instant.now().toString()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");
        logger.error("Validation error: {}", message);
        return new ErrorResponse(HttpStatus.BAD_REQUEST.toString(), message, Instant.now().toString());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ItemRequest {
        @NotBlank
        private String rawJson;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class IdResponse {
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ErrorResponse {
        private String error;
        private String message;
        private String timestamp;
    }
}