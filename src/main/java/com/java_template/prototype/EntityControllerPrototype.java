package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, HackerNewsItem> hackerNewsItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsItemIdCounter = new AtomicLong(1);

    @PostMapping("/hackernewsitem")
    public ResponseEntity<Map<String, String>> createHackerNewsItem(@RequestBody Map<String, Object> content) {
        log.info("Received POST request to create HackerNewsItem");

        // Validate presence of 'id' and 'type' fields in content
        Object hnId = content.get("id");
        Object hnType = content.get("type");
        String status;
        if (hnId == null || hnType == null) {
            status = "INVALID";
            log.info("Validation failed: 'id' or 'type' missing");
        } else {
            status = "VALIDATED";
            log.info("Validation succeeded");
        }

        // Generate UUID for business id and technicalId
        String uuid = UUID.randomUUID().toString();
        String technicalId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();

        // Convert content map to JSON string (simple approach)
        String contentJson = JsonUtil.toJson(content);

        HackerNewsItem item = new HackerNewsItem();
        item.setId(uuid);
        item.setTechnicalId(technicalId);
        item.setContent(contentJson);
        item.setTimestamp(timestamp);
        item.setStatus(status);

        hackerNewsItemCache.put(uuid, item);

        processHackerNewsItem(item);

        Map<String, String> response = new HashMap<>();
        response.put("uuid", uuid);
        response.put("status", status);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/hackernewsitem/{uuid}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String uuid) {
        log.info("Received GET request for HackerNewsItem with UUID: {}", uuid);

        HackerNewsItem item = hackerNewsItemCache.get(uuid);
        if (item == null) {
            log.error("HackerNewsItem not found for UUID: {}", uuid);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "HackerNewsItem not found for UUID: " + uuid));
        }

        Map<String, Object> response = new HashMap<>();
        // Parse the raw JSON content back into a map
        Map<String, Object> contentMap = JsonUtil.fromJson(item.getContent());
        if (contentMap == null) {
            contentMap = new HashMap<>();
        }
        response.putAll(contentMap);
        response.put("timestamp", item.getTimestamp().toString());
        response.put("status", item.getStatus());

        return ResponseEntity.ok(response);
    }

    private void processHackerNewsItem(HackerNewsItem entity) {
        log.info("Processing HackerNewsItem with ID: {}", entity.getId());

        // Business logic: Validation was done at creation, here we could enrich or notify
        // For this prototype, no additional processing beyond validation status and timestamp

        // Example enrichment: None needed now
        // Example notification: None needed now
        // Logging only
        log.info("HackerNewsItem status: {}", entity.getStatus());
    }

    // Utility class for JSON serialization/deserialization using Jackson ObjectMapper
    static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static String toJson(Object obj) {
            try {
                return mapper.writeValueAsString(obj);
            } catch (Exception e) {
                return "{}";
            }
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> fromJson(String json) {
            try {
                return mapper.readValue(json, Map.class);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // HackerNewsItem entity class for cache storage (minimal, for prototype)
    static class HackerNewsItem {
        private String id;  // business ID
        private String technicalId; // technical database ID
        private String content; // raw JSON content as string
        private Instant timestamp; // save timestamp
        private String status; // VALIDATED or INVALID

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTechnicalId() { return technicalId; }
        public void setTechnicalId(String technicalId) { this.technicalId = technicalId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}