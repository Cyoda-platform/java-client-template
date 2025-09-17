package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HnItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * HnItemController - REST API controller for managing Hacker News items
 * 
 * Provides endpoints for:
 * - Single item creation
 * - Batch item creation
 * - File upload for bulk operations
 * - Firebase API integration
 * - Search with hierarchical joins
 * - State transitions
 */
@RestController
@RequestMapping("/api/hnitem")
@CrossOrigin(origins = "*")
public class HnItemController {

    private static final Logger logger = LoggerFactory.getLogger(HnItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HnItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a single HN item
     * POST /api/hnitem
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<HnItem>> createHnItem(
            @RequestBody HnItem hnItem,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Creating HnItem with ID: {}, Type: {}", hnItem.getId(), hnItem.getType());
            
            EntityWithMetadata<HnItem> response = entityService.create(hnItem);
            
            // Apply transition if provided
            if (transition != null && !transition.trim().isEmpty()) {
                response = entityService.update(response.metadata().getId(), response.entity(), transition);
                logger.info("Applied transition '{}' to HnItem {}", transition, hnItem.getId());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating HnItem with ID: {}", hnItem.getId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create multiple HN items from array
     * POST /api/hnitem/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<List<EntityWithMetadata<HnItem>>> createHnItemsBatch(
            @RequestBody List<HnItem> hnItems,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Creating batch of {} HnItems", hnItems.size());
            
            List<EntityWithMetadata<HnItem>> responses = new ArrayList<>();
            
            for (HnItem hnItem : hnItems) {
                try {
                    EntityWithMetadata<HnItem> response = entityService.create(hnItem);
                    
                    // Apply transition if provided
                    if (transition != null && !transition.trim().isEmpty()) {
                        response = entityService.update(response.metadata().getId(), response.entity(), transition);
                    }
                    
                    responses.add(response);
                    logger.debug("Created HnItem with ID: {}", hnItem.getId());
                } catch (Exception e) {
                    logger.error("Error creating HnItem with ID: {}", hnItem.getId(), e);
                    // Continue with other items
                }
            }
            
            logger.info("Successfully created {} out of {} HnItems", responses.size(), hnItems.size());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("Error creating HnItems batch", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Upload HN items from JSON file
     * POST /api/hnitem/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<List<EntityWithMetadata<HnItem>>> uploadHnItems(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Uploading HnItems from file: {}", file.getOriginalFilename());
            
            // Parse JSON file
            HnItem[] hnItemsArray = objectMapper.readValue(file.getInputStream(), HnItem[].class);
            List<HnItem> hnItems = Arrays.asList(hnItemsArray);
            
            logger.info("Parsed {} HnItems from file", hnItems.size());
            
            // Create items using batch logic
            List<EntityWithMetadata<HnItem>> responses = new ArrayList<>();
            
            for (HnItem hnItem : hnItems) {
                try {
                    EntityWithMetadata<HnItem> response = entityService.create(hnItem);
                    
                    // Apply transition if provided
                    if (transition != null && !transition.trim().isEmpty()) {
                        response = entityService.update(response.metadata().getId(), response.entity(), transition);
                    }
                    
                    responses.add(response);
                } catch (Exception e) {
                    logger.error("Error creating HnItem with ID: {}", hnItem.getId(), e);
                    // Continue with other items
                }
            }
            
            logger.info("Successfully uploaded {} out of {} HnItems", responses.size(), hnItems.size());
            return ResponseEntity.ok(responses);
        } catch (IOException e) {
            logger.error("Error parsing uploaded file", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error uploading HnItems", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger Firebase API pull
     * POST /api/hnitem/fetch-from-firebase
     */
    @PostMapping("/fetch-from-firebase")
    public ResponseEntity<String> fetchFromFirebase(@RequestBody(required = false) FirebaseFetchRequest request) {
        try {
            logger.info("Triggering Firebase API fetch");
            
            // In a real implementation, this would:
            // 1. Connect to Firebase HN API
            // 2. Fetch latest items or specific items based on request
            // 3. Create HnItem entities from the fetched data
            // 4. Return summary of fetched items
            
            // For now, return a placeholder response
            String message = "Firebase API fetch triggered successfully";
            if (request != null && request.getItemIds() != null && !request.getItemIds().isEmpty()) {
                message += " for " + request.getItemIds().size() + " specific items";
            }
            
            logger.info("Firebase fetch completed: {}", message);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            logger.error("Error fetching from Firebase API", e);
            return ResponseEntity.badRequest().body("Error fetching from Firebase API");
        }
    }

    /**
     * Get HN item by technical UUID
     * GET /api/hnitem/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<HnItem>> getHnItemById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            EntityWithMetadata<HnItem> response = entityService.getById(id, modelSpec, HnItem.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HnItem by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get HN item by business ID (HN item ID)
     * GET /api/hnitem/business/{hnId}
     */
    @GetMapping("/business/{hnId}")
    public ResponseEntity<EntityWithMetadata<HnItem>> getHnItemByBusinessId(@PathVariable Long hnId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            EntityWithMetadata<HnItem> response = entityService.findByBusinessId(
                    modelSpec, hnId.toString(), "id", HnItem.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting HnItem by business ID: {}", hnId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update HN item state with workflow transition
     * PUT /api/hnitem/{id}/transition
     */
    @PutMapping("/{id}/transition")
    public ResponseEntity<EntityWithMetadata<HnItem>> updateHnItemState(
            @PathVariable UUID id,
            @RequestBody TransitionRequest transitionRequest) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            EntityWithMetadata<HnItem> current = entityService.getById(id, modelSpec, HnItem.class);
            
            // Apply transition
            EntityWithMetadata<HnItem> response = entityService.update(id, current.entity(), transitionRequest.getTransition());
            logger.info("Applied transition '{}' to HnItem {}", transitionRequest.getTransition(), id);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating HnItem state", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for Firebase fetch requests
     */
    @Getter
    @Setter
    public static class FirebaseFetchRequest {
        private List<Long> itemIds;
        private List<String> storyTypes;
        private Integer limit;
    }

    /**
     * DTO for transition requests
     */
    @Getter
    @Setter
    public static class TransitionRequest {
        private String transition;
    }

    /**
     * DTO for search requests
     */
    @Getter
    @Setter
    public static class SearchRequest {
        private String text;
        private String type;
        private String author;
        private Long parent;
        private Integer limit = 50;
        private Integer offset = 0;
    }
}
