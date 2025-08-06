package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HackerNewsItem;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/hackerNewsItems")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> createHackerNewsItem(@RequestBody ObjectNode hackerNewsItemJson) {
        try {
            // Use entityService to add item - validation and enrichment handled by workflow
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HackerNewsItem.ENTITY_NAME,
                    ENTITY_VERSION,
                    hackerNewsItemJson
            );

            UUID technicalId = idFuture.join();

            log.info("HackerNewsItem saved with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing HackerNewsItem creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid technicalId format"));
            }

            CompletableFuture<ObjectNode> itemWithMetaFuture = entityService.getItemWithMetaFields(
                    HackerNewsItem.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );

            ObjectNode itemWithMeta = itemWithMetaFuture.join();
            if (itemWithMeta == null) {
                log.error("HackerNewsItem not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "HackerNewsItem not found"));
            }

            // Extract item data from the data section
            JsonNode itemData = itemWithMeta.path("data");
            if (itemData.isMissingNode() || !itemData.isObject()) {
                log.error("Invalid item data structure for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Invalid item data structure"));
            }

            // Extract metadata from the meta section
            JsonNode metaNode = itemWithMeta.path("meta");
            String state = metaNode.path("state").asText(null);
            String creationDate = metaNode.path("creationDate").asText(null);

            // Build response with item and metadata at top level
            ObjectNode response = objectMapper.createObjectNode();
            response.set("item", itemData);
            response.put("technicalId", technicalId);
            if (state != null) {
                response.put("state", state);
            }
            if (creationDate != null) {
                response.put("creationDate", creationDate);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching HackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }


}