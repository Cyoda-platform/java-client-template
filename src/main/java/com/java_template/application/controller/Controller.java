package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.HackerNewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/hackerNewsItems")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> createHackerNewsItem(@RequestBody ObjectNode hackerNewsItemJson) throws JsonProcessingException {
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
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid technicalId format"));
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HackerNewsItem.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );

            ObjectNode item = itemFuture.join();
            if (item == null) {
                log.error("HackerNewsItem not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "HackerNewsItem not found"));
            }
            // Return the item with metadata fields
            ObjectNode response = objectMapper.createObjectNode();
            response.set("item", item);
            response.put("technicalId", technicalId);

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