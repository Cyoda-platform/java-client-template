package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final String ENTITY_MODEL = "HackerNewsItem";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/hackernewsitem")
    public ResponseEntity<Map<String, String>> createHackerNewsItem(@RequestBody Map<String, Object> content) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        item.setId(UUID.randomUUID());
        item.setContent(objectMapper.writeValueAsString(content));
        item.setTimestamp(Instant.now());
        item.setStatus(status);

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                item
        );
        UUID technicalId = idFuture.get();
        item.setTechnicalId(technicalId);

        Map<String, String> response = new HashMap<>();
        response.put("uuid", item.getId().toString());
        response.put("status", status);

        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/hackernewsitem/{uuid}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String uuid) throws ExecutionException, InterruptedException, JsonProcessingException {
        log.info("Received GET request for HackerNewsItem with UUID: {}", uuid);

        // create condition to find item with id = uuid
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", UUID.fromString(uuid))
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

        HackerNewsItem item = objectMapper.treeToValue(obj, HackerNewsItem.class);

        Map<String, Object> response = new HashMap<>();
        response.put("content", objectMapper.readValue(item.getContent(), Map.class));
        response.put("timestamp", item.getTimestamp() != null ? item.getTimestamp().toString() : null);
        response.put("status", item.getStatus());

        return ResponseEntity.ok(response);
    }

    static class HackerNewsItem {
        private UUID id;  // business ID
        private UUID technicalId; // technical database ID
        private String content; // JSON content as string
        private Instant timestamp; // save timestamp
        private String status; // VALIDATED or INVALID

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public UUID getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(UUID technicalId) {
            this.technicalId = technicalId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}