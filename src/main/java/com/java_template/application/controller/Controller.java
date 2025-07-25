package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HNItem;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/hnitems")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public ResponseEntity<?> createHNItem(@RequestBody Map<String, Object> hnItemPayload) {
        try {
            // Validate payload presence if needed (optional)
            if (hnItemPayload == null || hnItemPayload.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Payload is empty"));
            }

            // Create HNItem POJO from payload
            HNItem hnItem = new HNItem();

            // Generate technicalId as UUID string
            String technicalId = UUID.randomUUID().toString();
            hnItem.setTechnicalId(technicalId);

            // Extract original id as String if present
            String originalId = "";
            if (hnItemPayload.containsKey("id")) {
                Object idObj = hnItemPayload.get("id");
                originalId = idObj != null ? idObj.toString() : "";
            }
            hnItem.setId(originalId);

            // Serialize payload to JSON string to store in HNItem
            String payloadJson = objectMapper.writeValueAsString(hnItemPayload);
            hnItem.setPayload(payloadJson);

            hnItem.setStatus("INVALID");
            hnItem.setCreatedAt(Instant.now().toString());

            // Add item to external service asynchronously
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "HNItem",
                    ENTITY_VERSION,
                    hnItem
            );

            UUID returnedId = idFuture.get(); // Wait for completion

            // Trigger processing asynchronously (no await here)
            processHNItem(hnItem);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);

            logger.info("Created HNItem with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createHNItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error on creating HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getHNItem(@PathVariable String technicalId) {
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

            // Extract fields from ObjectNode
            // technicalId field is present, remove or keep as needed
            // Deserialize payload string back to Map

            String payloadStr = node.has("payload") && !node.get("payload").isNull()
                    ? node.get("payload").asText()
                    : "{}";

            Map<String, Object> payloadMap;
            try {
                payloadMap = objectMapper.readValue(payloadStr, Map.class);
            } catch (Exception e) {
                logger.error("Failed to deserialize HNItem payload for technicalId: {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to parse stored payload"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("technicalId", node.has("technicalId") ? node.get("technicalId").asText() : technicalId);
            response.put("id", node.has("id") ? node.get("id").asText() : "");
            response.put("payload", payloadMap);
            response.put("status", node.has("status") ? node.get("status").asText() : "");
            response.put("createdAt", node.has("createdAt") ? node.get("createdAt").asText() : "");

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
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving HNItem: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    private void processHNItem(HNItem entity) {
        logger.info("Processing HNItem with technicalId: {}", entity.getTechnicalId());

        try {
            Map<String, Object> payloadMap = objectMapper.readValue(entity.getPayload(), Map.class);
            boolean hasType = payloadMap.containsKey("type") && payloadMap.get("type") != null && !payloadMap.get("type").toString().isBlank();
            boolean hasId = payloadMap.containsKey("id") && payloadMap.get("id") != null && !payloadMap.get("id").toString().isBlank();

            if (hasType && hasId) {
                // Create new immutable HNItem version with status VALIDATED
                HNItem validatedItem = new HNItem();
                validatedItem.setTechnicalId(entity.getTechnicalId());
                validatedItem.setId(entity.getId());
                validatedItem.setPayload(entity.getPayload());
                validatedItem.setCreatedAt(entity.getCreatedAt());
                validatedItem.setStatus("VALIDATED");

                // Save validated version by calling addItem with same technicalId? 
                // But EntityService does not have update - so we skip or add TODO.

                // TODO: Implement update operation when supported by EntityService.

                logger.info("HNItem with technicalId {} validated successfully", entity.getTechnicalId());
            } else {
                logger.info("HNItem with technicalId {} remains INVALID due to missing required fields", entity.getTechnicalId());
            }
        } catch (Exception e) {
            logger.error("Failed to process validation for HNItem with technicalId: {}", entity.getTechnicalId(), e);
        }
    }
}