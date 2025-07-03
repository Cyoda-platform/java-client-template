package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

@Validated
@RestController
@RequestMapping("/cyoda/entity/prototype/items")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("Controller initialized");
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public CompletableFuture<ResponseEntity<IdResponse>> storeItem(@RequestBody @Valid ItemRequest request) {
        ObjectNode entity;
        try {
            entity = (ObjectNode) objectMapper.readTree(request.getRawJson());
        } catch (Exception e) {
            logger.error("Invalid JSON provided", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format");
        }

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

    // DTO classes
    public static class ItemRequest {
        @NotBlank
        private String rawJson;

        public ItemRequest() {}

        public ItemRequest(String rawJson) {
            this.rawJson = rawJson;
        }

        public String getRawJson() {
            return rawJson;
        }

        public void setRawJson(String rawJson) {
            this.rawJson = rawJson;
        }
    }

    public static class IdResponse {
        private String id;

        public IdResponse() {}

        public IdResponse(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class ErrorResponse {
        private String error;
        private String message;
        private String timestamp;

        public ErrorResponse() {}

        public ErrorResponse(String error, String message, String timestamp) {
            this.error = error;
            this.message = message;
            this.timestamp = timestamp;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}