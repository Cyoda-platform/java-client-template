package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, JsonNode> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdResponse> saveItem(
            @RequestBody @Valid SaveItemRequest request) {
        logger.info("POST /prototype/items - received save request");
        JsonNode itemJson;
        try {
            itemJson = objectMapper.readTree(request.getItemJson());
        } catch (Exception e) {
            logger.error("Invalid JSON format", e);
            throw new ResponseStatusException(ResponseStatusException.Status.BAD_REQUEST.getStatusCode(), "Invalid JSON");
        }
        String id = UUID.randomUUID().toString();
        store.put(id, itemJson);
        logger.info("Item stored with id {}", id);
        return ResponseEntity.ok(new IdResponse(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getItem(
            @PathVariable("id") @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$") String id) {
        logger.info("GET /prototype/items/{} - retrieving item", id);
        JsonNode item = store.get(id);
        if (item == null) {
            logger.error("Item with id {} not found", id);
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND.getStatusCode(), "Item not found");
        }
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SaveItemRequest {
        @NotBlank
        @Size(max = 10000)
        private String itemJson;
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
    }
}