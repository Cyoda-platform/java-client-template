package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/hackerNewsItems")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public ResponseEntity<?> createHackerNewsItem(@RequestBody ObjectNode hackerNewsItemJson) {
        try {
            // Validate mandatory fields 'id' and 'type'
            if (!hackerNewsItemJson.hasNonNull("id")) {
                log.error("Missing mandatory field 'id'");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Missing mandatory field 'id'"));
            }
            if (!hackerNewsItemJson.hasNonNull("type") || hackerNewsItemJson.get("type").asText().isBlank()) {
                log.error("Missing or blank mandatory field 'type'");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Missing or blank mandatory field 'type'"));
            }

            // Enrich with importTimestamp
            String importTimestamp = Instant.now().toString();
            ObjectNode enrichedItem = hackerNewsItemJson.deepCopy();
            enrichedItem.put("importTimestamp", importTimestamp);

            // Use entityService to add item asynchronously
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HackerNewsItem.ENTITY_NAME,
                    ENTITY_VERSION,
                    enrichedItem
            );

            UUID technicalId = idFuture.join();

            log.info("HackerNewsItem saved with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing HackerNewsItem creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid technicalId format"));
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HackerNewsItem.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );

            ObjectNode item = itemFuture.join();
            if (item == null) {
                log.error("HackerNewsItem not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "HackerNewsItem not found"));
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching HackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // Dummy HackerNewsItem class to hold ENTITY_NAME constant
    private static class HackerNewsItem {
        public static final String ENTITY_NAME = "HackerNewsItem";
    }
}
