```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, JsonNode> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdResponse> saveItem(@RequestBody JsonNode itemJson) {
        logger.info("Received POST request to save Hacker News item");

        // Generate UUID for the new item
        String id = UUID.randomUUID().toString();

        // Store the item JSON against the generated ID
        store.put(id, itemJson);
        logger.info("Stored item with id {}", id);

        return ResponseEntity.ok(new IdResponse(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getItem(@PathVariable("id") String id) {
        logger.info("Received GET request for item with id {}", id);

        JsonNode item = store.get(id);
        if (item == null) {
            logger.error("Item with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }

        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
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
```