package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HackerNewsItem;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/hacker-news")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final String ENTITY_NAME = "HackerNewsItem";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/items")
    public ResponseEntity<?> createHackerNewsItem(@Valid @RequestBody HackerNewsItem item) {
        try {
            if (item == null) {
                logger.error("Received null HackerNewsItem");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!item.isValid()) {
                logger.error("Invalid HackerNewsItem data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    item
            );
            UUID technicalId = idFuture.get();
            logger.info("Created HackerNewsItem with technicalId: {}", technicalId);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception creating HackerNewsItem", e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/items/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("HackerNewsItem not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            HackerNewsItem item = objectMapper.treeToValue(node, HackerNewsItem.class);
            logger.info("Retrieved HackerNewsItem with technicalId: {}", technicalId);
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception retrieving HackerNewsItem", e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}