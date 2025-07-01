package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda/items")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> saveItem(@Valid @RequestBody ItemRequest request) {
        logger.info("Received POST request to save item");
        ObjectNode data;
        try {
            data = (ObjectNode) entityService.getObjectMapper().readTree(request.getRawJson());
        } catch (Exception e) {
            logger.error("Invalid JSON format", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format");
        }

        // Add item with workflow function processCyoda applied asynchronously before persistence
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "cyoda", ENTITY_VERSION,
                data,
                this::processCyoda
        );

        return idFuture.thenApply(id -> {
            logger.info("Stored item with technicalId: {}", id);
            return ResponseEntity.ok(new IdResponse(id.toString()));
        });
    }

    /**
     * Workflow function processCyoda.
     * All asynchronous logic, entity enrichment, and entity state changes before persistence 
     * should be done here.
     * 
     * Important: Do NOT add/update/delete 'cyoda' entities here to avoid infinite recursion.
     */
    private CompletableFuture<ObjectNode> processCyoda(ObjectNode entity) {
        // Add processed timestamp
        entity.put("processedTimestamp", Instant.now().toString());

        // Example: asynchronously enrich entity with supplementary data count
        CompletableFuture<Void> enrichmentFuture = entityService.searchItems(
                "supplementaryModel", ENTITY_VERSION, null
        ).thenAccept(results -> {
            int count = (results != null && results.isArray()) ? results.size() : 0;
            entity.put("supplementaryCount", count);
        }).exceptionally(ex -> {
            logger.error("Error in enrichment during processCyoda workflow", ex);
            // Do not fail the workflow, just log error and continue
            return null;
        });

        // Return entity after enrichment completes
        return enrichmentFuture.thenApply(v -> entity);
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<ObjectNode>> getItemById(@PathVariable @NotBlank String id) {
        logger.info("Received GET request for item with technicalId: {}", id);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for id: {}", id, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "cyoda", ENTITY_VERSION, technicalId
        );
        return itemFuture.thenApply(item -> {
            if (item == null || item.isEmpty()) {
                logger.error("Item not found with technicalId: {}", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
            }
            return ResponseEntity.ok(item);
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: status={}, message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(
                ex.getStatusCode().toString(),
                ex.getReason(),
                Instant.now().toString()
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse(ex.getMessage());
        logger.error("Validation error: {}", msg);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.toString(),
                msg,
                Instant.now().toString()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
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