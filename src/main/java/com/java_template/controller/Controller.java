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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Validated
@RestController
@RequestMapping("/cyoda/items")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Inject ObjectMapper via constructor
    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> saveItem(@Valid @RequestBody ItemRequest request) {
        logger.info("Received POST request to save item");
        ObjectNode data;
        try {
            data = (ObjectNode) objectMapper.readTree(request.getRawJson());
        } catch (Exception e) {
            logger.error("Invalid JSON format", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "cyoda", com.java_template.common.config.Config.ENTITY_VERSION,
                data
        );

        return idFuture.thenApply(id -> {
            logger.info("Stored item with technicalId: {}", id);
            return ResponseEntity.ok(new IdResponse(id.toString()));
        });
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
                "cyoda", com.java_template.common.config.Config.ENTITY_VERSION, technicalId
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

    // DTO classes

    public static class ItemRequest {
        @NotBlank
        private String rawJson;

        public ItemRequest() {
        }

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

        public IdResponse() {
        }

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

        public ErrorResponse() {
        }

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