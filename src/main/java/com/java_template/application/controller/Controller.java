package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HackerNewsItem;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/hackerNews")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/hackerNewsItems")
    public ResponseEntity<?> createHackerNewsItem(@RequestBody @Valid HackerNewsItem hackerNewsItem) throws JsonProcessingException {
        try {
            // Validate mandatory fields
            if (hackerNewsItem.getId() == null || hackerNewsItem.getId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "'id' field is mandatory and must not be blank"));
            }
            if (hackerNewsItem.getType() == null || hackerNewsItem.getType().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "'type' field is mandatory and must not be blank"));
            }

            hackerNewsItem.setCreatedAt(new Date().toInstant().toString());
            hackerNewsItem.setState("UNKNOWN");

            // Use entityService to add item
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "hackerNewsItem",
                    ENTITY_VERSION,
                    hackerNewsItem
            );

            UUID technicalId = idFuture.get();

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/hackerNewsItems/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) throws JsonProcessingException {
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

            HackerNewsItem item = objectMapper.treeToValue(itemNode, HackerNewsItem.class);

            Map<String, Object> response = new HashMap<>();
            response.put("item", item);
            response.put("technicalId", technicalId);
            response.put("state", item.getState() != null ? item.getState() : "UNKNOWN");
            response.put("createdAt", item.getCreatedAt());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid technicalId format"));
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}