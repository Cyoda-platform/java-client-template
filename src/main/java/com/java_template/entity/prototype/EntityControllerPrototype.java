package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private final Map<String, JsonNode> storage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse storeItem(@RequestBody @Valid @NotNull JsonNode hnItemPayload) {
        log.info("Received POST /prototype/items request");
        String generatedId = UUID.randomUUID().toString();
        storage.put(generatedId, hnItemPayload);
        log.info("Stored Hacker News item with generated id: {}", generatedId);
        return new IdResponse(generatedId);
    }

    @GetMapping(path = "/items/{id}")
    public JsonNode getItemById(@PathVariable("id") @NotBlank String id) {
        log.info("Received GET /prototype/items/{} request", id);
        JsonNode item = storage.get(id);
        if (item == null) {
            log.error("Item not found for id: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item with id " + id + " not found");
        }
        return item;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = ex.getStatusCode();
        log.error("Handled exception: status={}, message={}", status, ex.getReason());
        return new ErrorResponse(status.toString(), ex.getReason());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class IdResponse {
        @NotBlank
        private String id;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ErrorResponse {
        @NotBlank
        private String error;
        @NotBlank
        private String message;
    }
}