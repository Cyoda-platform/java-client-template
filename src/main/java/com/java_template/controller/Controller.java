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

    @PostMapping(consumes = "application/json", produces = "application/json")
    public CompletableFuture<ResponseEntity<IdResponse>> storeItem(@RequestBody @Valid ItemRequest request) {
        ObjectNode entity;
        try {
            entity = (ObjectNode) entityService.getObjectMapper().readTree(request.getRawJson());
        } catch (Exception e) {
            logger.error("Invalid JSON provided", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format");
        }

        // Note: Workflow argument removed
        return entityService.addItem(
                        "prototype",
                        ENTITY_VERSION,
                        entity
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
