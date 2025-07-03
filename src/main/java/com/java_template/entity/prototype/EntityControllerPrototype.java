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
import org.springframework.validation.MethodArgumentNotValidException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/items")
public class EntityControllerPrototype {

    private final Map<String, JsonNode> storedItems = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<IdResponse> storeItem(@RequestBody @Valid ItemRequest request) {
        JsonNode itemJson;
        try {
            itemJson = objectMapper.readTree(request.getRawJson());
        } catch (Exception e) {
            log.error("Invalid JSON provided", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format");
        }
        String id = UUID.randomUUID().toString();
        storedItems.put(id, itemJson);
        log.info("Stored item with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getItemById(@PathVariable @NotBlank String id) {
        JsonNode item = storedItems.get(id);
        if (item == null) {
            log.warn("Item not found for ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item with ID " + id + " not found");
        }
        log.info("Retrieved item with ID: {}", id);
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
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
        log.error("Validation error: {}", message);
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