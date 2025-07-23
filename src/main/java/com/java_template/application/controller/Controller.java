package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HNItem;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/hnitems")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "HNItem";

    @PostMapping
    public ResponseEntity<?> createHNItem(@RequestBody Map<String, Object> hnItemPayload) throws ExecutionException, InterruptedException {
        // Validate required fields presence
        if (!hnItemPayload.containsKey("id") || !hnItemPayload.containsKey("type")) {
            log.error("Validation failed: Missing required fields 'id' or 'type'");
            return ResponseEntity.badRequest().body("Missing required fields 'id' or 'type'");
        }

        // Construct HNItem entity from payload
        HNItem hnItem = new HNItem();
        hnItem.setId(null); // business id is not used for storage, will be preserved in content
        hnItem.setType(hnItemPayload.get("type").toString());
        try {
            hnItem.setContent(objectMapper.writeValueAsString(hnItemPayload));
        } catch (Exception e) {
            log.error("Failed to serialize content", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid content format");
        }
        hnItem.setStatus("INVALID"); // initial status

        // Add item to external entity service (will assign technicalId)
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, hnItem);
        UUID technicalId = idFuture.get();

        // Retrieve the stored entity to get technicalId and full data
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode storedNode = itemFuture.get();

        // Deserialize to HNItem for processing
        HNItem storedItem = objectMapper.convertValue(storedNode, HNItem.class);

        // Store updated entity with updated status as a new entity version (event-driven architecture)
        CompletableFuture<UUID> updatedIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, storedItem);
        UUID updatedTechnicalId = updatedIdFuture.get();

        // Return generated business ID from content JSON if exists or technicalId as fallback
        Map<String, String> response = new HashMap<>();
        try {
            Map<?, ?> contentMap = objectMapper.readValue(storedItem.getContent(), Map.class);
            Object contentId = contentMap.get("id");
            if (contentId != null && !contentId.toString().isBlank()) {
                response.put("id", contentId.toString());
            } else {
                response.put("id", updatedTechnicalId.toString());
            }
        } catch (Exception e) {
            response.put("id", updatedTechnicalId.toString());
        }

        log.info("HNItem created with technicalId: {}", updatedTechnicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getHNItem(@PathVariable String id) throws ExecutionException, InterruptedException {
        // Since technicalId is UUID, try to parse id as UUID first, else search by content.id

        UUID technicalId = null;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
        }

        ObjectNode itemNode = null;

        if (technicalId != null) {
            // Try to get item by technicalId
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
            itemNode = itemFuture.get();
        }

        if (itemNode == null || itemNode.isEmpty()) {
            // Search by content.id (business id)
            // Build condition: content.id EQUALS id
            // But content is serialized string, so we cannot query by nested JSON path content.id, so fallback to inMemory search and filter manually

            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    ENTITY_NAME, ENTITY_VERSION,
                    com.java_template.common.util.SearchConditionRequest.group("AND",
                            com.java_template.common.util.Condition.of("$.content", "CONTAINS", "\"id\":\"" + id + "\"")
                    ),
                    true
            );

            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();

            if (items.size() > 0) {
                itemNode = (ObjectNode) items.get(0);
                technicalId = UUID.fromString(itemNode.get("technicalId").asText());
            }
        }

        if (itemNode == null || itemNode.isEmpty()) {
            log.error("HNItem not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("HNItem not found");
        }

        // Deserialize to HNItem to return structured response
        HNItem hnItem = objectMapper.convertValue(itemNode, HNItem.class);

        Map<String, Object> response = new HashMap<>();
        response.put("id", hnItem.getId());
        response.put("type", hnItem.getType());
        response.put("content", hnItem.getContent());
        response.put("status", hnItem.getStatus());

        log.info("HNItem retrieved with technicalId: {}", technicalId);
        return ResponseEntity.ok(response);
    }

}