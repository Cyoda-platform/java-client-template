package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/items")
public class EntityControllerPrototype {

    private final Map<String, JsonNode> itemStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public ResponseEntity<IdResponse> saveItem(@Valid @RequestBody ItemRequest request) {
        log.info("Received POST request to save Hacker News item");
        JsonNode hnItem;
        try {
            hnItem = objectMapper.readTree(request.getRawJson());
        } catch (Exception e) {
            log.error("Invalid JSON format", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format");
        }
        String generatedId = UUID.randomUUID().toString();
        itemStore.put(generatedId, hnItem);
        log.info("Stored item with generated ID: {}", generatedId);
        return ResponseEntity.ok(new IdResponse(generatedId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getItemById(@PathVariable @NotBlank String id) {
        log.info("Received GET request for item with ID: {}", id);
        JsonNode item = itemStore.get(id);
        if (item == null) {
            log.error("Item not found with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling ResponseStatusException: status={}, message={}", ex.getStatusCode(), ex.getReason());
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
        log.error("Validation error: {}", msg);
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