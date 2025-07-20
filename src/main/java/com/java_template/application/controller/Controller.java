package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;
import java.util.UUID;
import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
public class Controller {

    private final EntityService entityService;
    private static final String ENTITY_MODEL = "HackerNewsItem";

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/hackernewsitem")
    public ResponseEntity<Map<String, String>> createHackerNewsItem(@RequestBody Map<String, Object> content) throws ExecutionException, InterruptedException {
        log.info("Received POST request to create HackerNewsItem");

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

        HackerNewsItem item = new HackerNewsItem();
        item.setId(UUID.randomUUID().toString());
        item.setContent(content);
        item.setTimestamp(Instant.now());
        item.setStatus(status);

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                item
        );
        UUID technicalId = idFuture.get();
        item.setTechnicalId(technicalId.toString());

        processHackerNewsItem(item);

        Map<String, String> response = new HashMap<>();
        response.put("uuid", item.getId());
        response.put("status", status);

        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/hackernewsitem/{uuid}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String uuid) throws ExecutionException, InterruptedException {
        log.info("Received GET request for HackerNewsItem with UUID: {}", uuid);

        // create condition to find item with id = uuid
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", uuid)
        );

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_MODEL,
                ENTITY_VERSION,
                condition
        );

        ArrayNode items = itemsFuture.get();

        if (items == null || items.size() == 0) {
            log.error("HackerNewsItem not found for UUID: {}", uuid);
            return ResponseEntity.status(404)
                    .body(Map.of("error", "HackerNewsItem not found for UUID: " + uuid));
        }

        ObjectNode obj = (ObjectNode) items.get(0);

        HackerNewsItem item = new HackerNewsItem();
        item.setTechnicalId(obj.get("technicalId").asText());
        item.setId(obj.get("id").asText());
        item.setStatus(obj.has("status") ? obj.get("status").asText() : null);
        item.setTimestamp(obj.has("timestamp") ? Instant.parse(obj.get("timestamp").asText()) : null);
        item.setContent(obj.has("content") ? JsonUtil.toMap(obj.get("content")) : new HashMap<>());

        Map<String, Object> response = new HashMap<>();
        response.put("content", item.getContent());
        response.put("timestamp", item.getTimestamp() != null ? item.getTimestamp().toString() : null);
        response.put("status", item.getStatus());

        return ResponseEntity.ok(response);
    }

    private void processHackerNewsItem(HackerNewsItem entity) {
        log.info("Processing HackerNewsItem with ID: {}", entity.getId());

        // Business logic: Validation was done at creation, here we could enrich or notify
        // For this prototype, no additional processing beyond validation status and timestamp
        log.info("HackerNewsItem status: {}", entity.getStatus());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class HackerNewsItem {
        private String id;  // business ID
        private String technicalId; // technical database ID
        private Map<String, Object> content; // parsed JSON content as map
        private Instant timestamp; // save timestamp
        private String status; // VALIDATED or INVALID
    }

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
        static Map<String, Object> toMap(com.fasterxml.jackson.databind.JsonNode node) {
            try {
                return mapper.convertValue(node, Map.class);
            } catch (Exception e) {
                return new HashMap<>();
            }
        }
    }
}