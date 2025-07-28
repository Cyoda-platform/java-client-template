package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, HackerNewsItem> hackerNewsItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsItemIdCounter = new AtomicLong(1);

    @PostMapping("/items")
    public ResponseEntity<Map<String, String>> createHackerNewsItem(@RequestBody HackerNewsItem item) {
        if (item == null) {
            log.error("Received null HackerNewsItem");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!item.isValid()) {
            log.error("Invalid HackerNewsItem data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(hackerNewsItemIdCounter.getAndIncrement());
        hackerNewsItemCache.put(technicalId, item);
        log.info("Created HackerNewsItem with technicalId: {}", technicalId);
        processHackerNewsItem(technicalId, item);
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/items/{technicalId}")
    public ResponseEntity<HackerNewsItem> getHackerNewsItem(@PathVariable String technicalId) {
        HackerNewsItem item = hackerNewsItemCache.get(technicalId);
        if (item == null) {
            log.error("HackerNewsItem not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Retrieved HackerNewsItem with technicalId: {}", technicalId);
        return ResponseEntity.ok(item);
    }

    private void processHackerNewsItem(String technicalId, HackerNewsItem entity) {
        // Validation: Ensure required fields are present and meaningful
        if (entity.getHnId() == null || entity.getHnId() <= 0) {
            log.error("Invalid hnId in HackerNewsItem with technicalId: {}", technicalId);
            return;
        }
        if (entity.getType() == null || entity.getType().isBlank()) {
            log.error("Invalid type in HackerNewsItem with technicalId: {}", technicalId);
            return;
        }
        if (entity.getTime() == null || entity.getTime() <= 0) {
            log.error("Invalid time in HackerNewsItem with technicalId: {}", technicalId);
            return;
        }
        if (entity.getBy() == null || entity.getBy().isBlank()) {
            log.error("Invalid author (by) in HackerNewsItem with technicalId: {}", technicalId);
            return;
        }

        // Normalize kids list to empty if null
        if (entity.getKids() == null) {
            entity.setKids(new ArrayList<>());
            log.info("Normalized kids list to empty for HackerNewsItem with technicalId: {}", technicalId);
        }

        // Indexing or other preparation would be done here if needed

        // Mark processing complete (conceptual since data is immutable)
        log.info("Processed HackerNewsItem with technicalId: {}", technicalId);

        // Optional: Trigger downstream events or notifications if applicable (not implemented)
    }
}