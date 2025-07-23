package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.HNItem;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, HNItem> hnItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hnItemIdCounter = new AtomicLong(1);

    @PostMapping("/hnitems")
    public ResponseEntity<?> createHNItem(@RequestBody Map<String, Object> hnItemPayload) {
        try {
            // Validate required fields presence
            if (!hnItemPayload.containsKey("id") || !hnItemPayload.containsKey("type")) {
                log.error("Validation failed: Missing required fields 'id' or 'type'");
                return ResponseEntity.badRequest().body("Missing required fields 'id' or 'type'");
            }

            // Generate unique business ID as string
            String generatedId = String.valueOf(hnItemIdCounter.getAndIncrement());

            // Construct HNItem entity
            HNItem hnItem = new HNItem();
            hnItem.setId(generatedId);
            hnItem.setType(hnItemPayload.get("type").toString());
            hnItem.setContent(hnItemPayload);
            hnItem.setStatus("INVALID"); // initial status
            hnItem.setTechnicalId(UUID.randomUUID());

            // Save entity to cache
            hnItemCache.put(hnItem.getId(), hnItem);

            // Trigger event processing
            processHNItem(hnItem);

            // Return generated ID
            Map<String, String> response = new HashMap<>();
            response.put("id", hnItem.getId());
            log.info("HNItem created with ID: {}", hnItem.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/hnitems/{id}")
    public ResponseEntity<?> getHNItem(@PathVariable String id) {
        HNItem hnItem = hnItemCache.get(id);
        if (hnItem == null) {
            log.error("HNItem not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("HNItem not found");
        }
        // Return stored item including status and full content
        Map<String, Object> response = new HashMap<>();
        response.put("id", hnItem.getId());
        response.put("type", hnItem.getType());
        response.put("content", hnItem.getContent());
        response.put("status", hnItem.getStatus());
        log.info("HNItem retrieved with ID: {}", id);
        return ResponseEntity.ok(response);
    }

    private void processHNItem(HNItem entity) {
        log.info("Processing HNItem with ID: {}", entity.getId());

        // Validation logic: check presence of "id" and "type" in content JSON
        Object contentId = entity.getContent().get("id");
        Object contentType = entity.getContent().get("type");
        if (contentId == null || contentType == null || contentId.toString().isBlank() || contentType.toString().isBlank()) {
            // Remain in INVALID state
            entity.setStatus("INVALID");
            log.info("HNItem ID {} validation failed - missing 'id' or 'type'", entity.getId());
        } else {
            // Update status to VALIDATED
            entity.setStatus("VALIDATED");
            log.info("HNItem ID {} validation succeeded - status set to VALIDATED", entity.getId());
        }

        // Update cache with new status
        hnItemCache.put(entity.getId(), entity);
    }
}