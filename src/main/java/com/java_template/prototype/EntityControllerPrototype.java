package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.HNItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, HNItem> hnItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hnItemIdCounter = new AtomicLong(1);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/hnitems")
    public ResponseEntity<Map<String, String>> createHNItem(@RequestBody Map<String, Object> hnItemPayload) {
        try {
            // Generate new technicalId UUID
            String technicalId = UUID.randomUUID().toString();

            // Extract original id as String if present
            String originalId = "";
            if (hnItemPayload.containsKey("id")) {
                Object idObj = hnItemPayload.get("id");
                originalId = idObj != null ? idObj.toString() : "";
            }

            // Serialize payload back to JSON string
            String payloadJson = objectMapper.writeValueAsString(hnItemPayload);

            // Create HNItem entity with status INVALID initially
            HNItem hnItem = new HNItem();
            hnItem.setTechnicalId(technicalId);
            hnItem.setId(originalId);
            hnItem.setPayload(payloadJson);
            hnItem.setStatus("INVALID");
            hnItem.setCreatedAt(Instant.now().toString());

            // Save to cache
            hnItemCache.put(technicalId, hnItem);

            // Trigger processing
            processHNItem(hnItem);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);

            log.info("Created HNItem with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (JsonProcessingException e) {
            log.error("Failed to process HNItem payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid JSON payload"));
        } catch (Exception e) {
            log.error("Unexpected error on creating HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/hnitems/{technicalId}")
    public ResponseEntity<?> getHNItem(@PathVariable String technicalId) {
        HNItem hnItem = hnItemCache.get(technicalId);
        if (hnItem == null) {
            log.error("HNItem not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "HNItem not found"));
        }

        try {
            // Deserialize payload JSON string to Map
            Map<String, Object> payloadMap = objectMapper.readValue(hnItem.getPayload(), Map.class);

            Map<String, Object> response = new HashMap<>();
            response.put("technicalId", hnItem.getTechnicalId());
            response.put("id", hnItem.getId());
            response.put("payload", payloadMap);
            response.put("status", hnItem.getStatus());
            response.put("createdAt", hnItem.getCreatedAt());

            log.info("Retrieved HNItem with technicalId: {}", technicalId);
            return ResponseEntity.ok(response);

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize HNItem payload for technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to parse stored payload"));
        }
    }

    private void processHNItem(HNItem entity) {
        log.info("Processing HNItem with technicalId: {}", entity.getTechnicalId());

        // Validation: check presence of "type" and "id" fields in payload JSON
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

                // Save validated version in cache (overwrite with validated status)
                hnItemCache.put(validatedItem.getTechnicalId(), validatedItem);

                log.info("HNItem with technicalId {} validated successfully", entity.getTechnicalId());
            } else {
                log.info("HNItem with technicalId {} remains INVALID due to missing required fields", entity.getTechnicalId());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to process validation for HNItem with technicalId: {}", entity.getTechnicalId(), e);
        }
    }
}