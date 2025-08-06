package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, ObjectNode> hackerNewsItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsItemIdCounter = new AtomicLong(1);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/hackerNewsItems")
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

            // Generate technicalId as string of atomic long
            String technicalId = String.valueOf(hackerNewsItemIdCounter.getAndIncrement());

            // Save to cache
            hackerNewsItemCache.put(technicalId, enrichedItem);

            // Process entity event
            processHackerNewsItem(technicalId, enrichedItem);

            log.info("HackerNewsItem saved with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error processing HackerNewsItem creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/hackerNewsItems/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) {
        ObjectNode item = hackerNewsItemCache.get(technicalId);
        if (item == null) {
            log.error("HackerNewsItem not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "HackerNewsItem not found"));
        }
        return ResponseEntity.ok(item);
    }

    private void processHackerNewsItem(String technicalId, ObjectNode entity) {
        // Business logic for processing HackerNewsItem:
        // - Validation already done in controller
        // - Enrichment done before saving
        // - No external API calls specified
        // - No workflows or notifications required
        // This method can be extended as needed for future logic
        log.info("Processing HackerNewsItem with technicalId: {}", technicalId);
    }
}