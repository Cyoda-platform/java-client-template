package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, JsonNode> datastore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE) // must be first
    public ResponseEntity<IdResponse> postItem(
            @RequestBody @NotNull JsonNode itemJson // validate non-null JSON payload
    ) {
        logger.info("Received POST /prototype/items request");
        String generatedId = UUID.randomUUID().toString();
        logger.info("Generated ID: {}", generatedId);
        datastore.put(generatedId, itemJson);
        logger.info("Item stored with ID {}", generatedId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(generatedId));
    }

    @GetMapping("/{id}") // must be first
    public ResponseEntity<JsonNode> getItem(
            @PathVariable("id")
            @Pattern(regexp = "[0-9a-fA-F\\-]{36}", message = "Invalid ID format") String id
    ) {
        logger.info("Received GET /prototype/items/{} request", id);
        JsonNode item = datastore.get(id);
        if (item == null) {
            logger.error("Item not found for ID {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        logger.info("Item found for ID {}", id);
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