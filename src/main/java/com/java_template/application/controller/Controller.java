package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HNItem;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/hnitems")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> createHNItem(@RequestBody Map<String, Object> hnItemPayload) throws JsonProcessingException {
        try {
            if (hnItemPayload == null || hnItemPayload.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Payload is empty"));
            }

            HNItem hnItem = new HNItem();
            UUID technicalUUID = UUID.randomUUID();
            hnItem.setTechnicalId(technicalUUID.toString());

            String originalId = "";
            if (hnItemPayload.containsKey("id")) {
                Object idObj = hnItemPayload.get("id");
                originalId = idObj != null ? idObj.toString() : "";
            }
            hnItem.setId(originalId);

            String payloadJson = objectMapper.writeValueAsString(hnItemPayload);
            hnItem.setPayload(payloadJson);

            hnItem.setStatus("INVALID");
            hnItem.setCreatedAt(Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "HNItem",
                    ENTITY_VERSION,
                    hnItem
            );

            UUID returnedId = idFuture.get();

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", returnedId.toString());

            logger.info("Created HNItem with technicalId: {}", returnedId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createHNItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (ExecutionException e) {
            logger.error("Execution error on creating HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted on creating HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error on creating HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getHNItem(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "HNItem",
                    ENTITY_VERSION,
                    technicalUUID
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("HNItem not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "HNItem not found"));
            }

            HNItem hnItem = objectMapper.treeToValue(node, HNItem.class);

            Map<String, Object> payloadMap = objectMapper.readValue(hnItem.getPayload(), Map.class);

            Map<String, Object> response = new HashMap<>();
            response.put("technicalId", hnItem.getTechnicalId());
            response.put("id", hnItem.getId());
            response.put("payload", payloadMap);
            response.put("status", hnItem.getStatus());
            response.put("createdAt", hnItem.getCreatedAt());

            logger.info("Retrieved HNItem with technicalId: {}", technicalId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument when retrieving HNItem: {}", technicalId, cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("Execution error when retrieving HNItem: {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted when retrieving HNItem: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving HNItem: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}