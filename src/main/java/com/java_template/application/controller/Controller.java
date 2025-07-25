package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/hackerNews")
@Slf4j
@AllArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/hackerNewsItems")
    public ResponseEntity<?> createHackerNewsItem(@RequestBody Map<String, Object> payload) {
        try {
            // Validate mandatory fields
            Object idObj = payload.get("id");
            Object typeObj = payload.get("type");
            if (idObj == null || (idObj instanceof String && ((String) idObj).isBlank())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "'id' field is mandatory and must not be blank"));
            }
            if (typeObj == null || (typeObj instanceof String && ((String) typeObj).isBlank())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "'type' field is mandatory and must not be blank"));
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

            // Use entityService to add item
            // entityService.addItem expects validated data object - we will pass the entity as Map
            Map<String, Object> entityMap = new HashMap<>(payload);
            entityMap.put("createdAt", entity.getCreatedAt());
            entityMap.put("state", entity.getState());

            // Add item asynchronously
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "hackerNewsItem",
                    ENTITY_VERSION,
                    entityMap
            );

            UUID technicalId = idFuture.get();

            logger.info("Created HackerNewsItem with id: {}, technicalId: {}", entity.getId(), technicalId.toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to add item via entityService", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error in createHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/hackerNewsItems/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "hackerNewsItem",
                    ENTITY_VERSION,
                    technicalUUID
            );

            ObjectNode itemNode = itemFuture.get();

            if (itemNode == null || itemNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Item with technicalId " + technicalId + " not found."));
            }

            // Extract stored state and createdAt if present or fallback
            String state = "UNKNOWN";
            String createdAt = null;
            if (itemNode.has("state") && !itemNode.get("state").isNull()) {
                state = itemNode.get("state").asText("UNKNOWN");
            }
            if (itemNode.has("createdAt") && !itemNode.get("createdAt").isNull()) {
                createdAt = itemNode.get("createdAt").asText(null);
            }

            // Remove technicalId from the item data to avoid duplication in response map
            itemNode.remove("technicalId");

            // Convert ObjectNode to Map for response
            Map<String, Object> itemMap = objectMapper.convertValue(itemNode, Map.class);

            Map<String, Object> response = new HashMap<>();
            response.put("item", itemMap);
            response.put("technicalId", technicalId);
            response.put("state", state);
            response.put("createdAt", createdAt);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid technicalId format"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to get item via entityService for technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error in getHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Entity class definition
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class HackerNewsItem {
        private String rawJson;
        private String id;
        private String type;
        private String state;
        private String createdAt;
    }
}
