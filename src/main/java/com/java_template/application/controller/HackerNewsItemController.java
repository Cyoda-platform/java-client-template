package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.store.InMemoryDataStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/hackerNewsItems")
public class HackerNewsItemController {

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping
    public ResponseEntity<Map<String, String>> createItem(@RequestBody JsonNode payload) {
        HackerNewsItem item = new HackerNewsItem();
        item.setTechnicalId("item-" + UUID.randomUUID());
        item.setOriginalJson(payload);
        if (payload.has("id") && payload.get("id").isNumber()) {
            item.setId(payload.get("id").longValue());
        }
        if (payload.has("type") && payload.get("type").isTextual()) {
            item.setType(payload.get("type").asText());
        }
        item.setStatus("PENDING");
        item.setCreatedAt(Instant.now().toString());

        // store minimal metadata and let processors act (in this prototype we directly run processors)
        InMemoryDataStore.itemsByTechnicalId.put(item.getTechnicalId(), item);

        return ResponseEntity.ok(Map.of("technicalId", item.getTechnicalId()));
    }

    @GetMapping("/technical/{technicalId}")
    public ResponseEntity<HackerNewsItem> getByTechnicalId(@PathVariable String technicalId) {
        HackerNewsItem item = InMemoryDataStore.itemsByTechnicalId.get(technicalId);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<JsonNode> getByHnId(@PathVariable Long id) {
        JsonNode node = InMemoryDataStore.itemsByHnId.get(id);
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(node);
    }
}
