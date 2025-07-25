package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, HackerNewsItemWrapper> hackerNewsItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsItemIdCounter = new AtomicLong(1);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Wrapper to hold entity and metadata
    private static class HackerNewsItemWrapper {
        private final String technicalId;
        private final HackerNewsItem entity;

        public HackerNewsItemWrapper(String technicalId, HackerNewsItem entity) {
            this.technicalId = technicalId;
            this.entity = entity;
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public HackerNewsItem getEntity() {
            return entity;
        }
    }

    @PostMapping("/hackerNewsItems")
    public ResponseEntity<?> createHackerNewsItem(@RequestBody Map<String,Object> payload) {
        try {
            // Validate mandatory fields
            Object idObj = payload.get("id");
            Object typeObj = payload.get("type");
            if (idObj == null || (idObj instanceof String && ((String)idObj).isBlank())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "'id' field is mandatory and must not be blank"));
            }
            if (typeObj == null || (typeObj instanceof String && ((String)typeObj).isBlank())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "'type' field is mandatory and must not be blank"));
            }

            // Convert payload back to JSON string (rawJson)
            String rawJson = objectMapper.writeValueAsString(payload);

            // Extract id and type as strings
            String idStr = String.valueOf(idObj);
            String typeStr = String.valueOf(typeObj);

            // Create new entity instance
            HackerNewsItem entity = new HackerNewsItem();
            entity.setId(idStr);
            entity.setType(typeStr);
            entity.setRawJson(rawJson);
            entity.setCreatedAt(new Date().toInstant().toString());
            entity.setState("UNKNOWN");

            // Process entity (includes validation)
            processHackerNewsItem(entity);

            // Generate technicalId and store immutable event
            String technicalId = UUID.randomUUID().toString();
            hackerNewsItemCache.put(technicalId, new HackerNewsItemWrapper(technicalId, entity));

            log.info("Created HackerNewsItem with id: {}, technicalId: {}", entity.getId(), technicalId);

            // Return only technicalId per requirements
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));

        } catch (JsonProcessingException e) {
            log.error("Failed to process request JSON", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid JSON input"));
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/hackerNewsItems/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) {
        HackerNewsItemWrapper wrapper = hackerNewsItemCache.get(technicalId);
        if (wrapper == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Item with technicalId " + technicalId + " not found."));
        }
        HackerNewsItem entity = wrapper.getEntity();

        // Parse rawJson back to Map to return exactly the POSTed HackerNewsItem
        Map<String, Object> itemMap;
        try {
            itemMap = objectMapper.readValue(entity.getRawJson(), Map.class);
        } catch (Exception e) {
            log.error("Failed to parse rawJson for HackerNewsItem {}", entity.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to parse stored item data"));
        }

        Map<String,Object> response = new HashMap<>();
        response.put("item", itemMap);
        response.put("technicalId", wrapper.getTechnicalId());
        response.put("state", entity.getState());
        response.put("createdAt", entity.getCreatedAt());

        return ResponseEntity.ok(response);
    }

    private void processHackerNewsItem(HackerNewsItem entity) {
        log.info("Processing HackerNewsItem with ID: {}", entity.getId());

        // Validation: check mandatory fields
        if (entity.getId() == null || entity.getId().isBlank() ||
            entity.getType() == null || entity.getType().isBlank()) {
            entity.setState("INVALID");
            log.info("HackerNewsItem {} marked INVALID due to missing mandatory fields", entity.getId());
        } else {
            entity.setState("VALID");
            log.info("HackerNewsItem {} marked VALID", entity.getId());
        }

        // Further processing could be added here (mocked)
    }

    // Entity class definition for the prototype (can be moved to entity package)
    @lombok.Data
    private static class HackerNewsItem {
        private String rawJson;
        private String id;
        private String type;
        private String state;
        private String createdAt;
    }
}
