package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: InventoryItemController provides REST endpoints for inventory management including
 * CRUD operations, stock adjustments, and inventory queries for the multi-channel retail system.
 */
@RestController
@RequestMapping("/ui/inventory")
@CrossOrigin(origins = "*")
public class InventoryItemController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InventoryItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new inventory item
     * POST /ui/inventory
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<InventoryItem>> createInventoryItem(@RequestBody InventoryItem inventoryItem) {
        try {
            // Initialize stock timestamps
            if (inventoryItem.getStockByLocation() != null) {
                inventoryItem.getStockByLocation().values().forEach(stock -> {
                    if (stock.getLastUpdated() == null) {
                        stock.setLastUpdated(LocalDateTime.now());
                    }
                });
            }

            EntityWithMetadata<InventoryItem> response = entityService.create(inventoryItem);
            logger.info("InventoryItem created with ID: {}, productId: {}", response.metadata().getId(), inventoryItem.getProductId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Error creating inventory item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get inventory item by technical ID
     * GET /ui/inventory/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> getInventoryItemById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(InventoryItem.ENTITY_NAME);
            modelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> response = entityService.getById(id, modelSpec, InventoryItem.class);
            logger.info("InventoryItem retrieved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving inventory item by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get inventory item by product ID
     * GET /ui/inventory/product/{productId}
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> getInventoryItemByProductId(@PathVariable String productId) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(InventoryItem.ENTITY_NAME);
            modelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> response = entityService.findByBusinessId(modelSpec, productId, "productId", InventoryItem.class);
            logger.info("InventoryItem retrieved with productId: {}", productId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving inventory item by productId: {}", productId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update inventory item with optional workflow transition
     * PUT /ui/inventory/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> updateInventoryItem(
            @PathVariable UUID id,
            @RequestBody InventoryItem inventoryItem,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<InventoryItem> response = entityService.update(id, inventoryItem, transition);
            logger.info("InventoryItem updated with ID: {}, transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating inventory item with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update inventory item by product ID with optional workflow transition
     * PUT /ui/inventory/product/{productId}?transition=TRANSITION_NAME
     */
    @PutMapping("/product/{productId}")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> updateInventoryItemByProductId(
            @PathVariable String productId,
            @RequestBody InventoryItem inventoryItem,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<InventoryItem> response = entityService.updateByBusinessId(inventoryItem, "productId", transition);
            logger.info("InventoryItem updated with productId: {}, transition: {}", productId, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating inventory item with productId: {}", productId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete inventory item by technical ID
     * DELETE /ui/inventory/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<UUID> deleteInventoryItem(@PathVariable UUID id) {
        try {
            UUID deletedId = entityService.deleteById(id);
            logger.info("InventoryItem deleted with ID: {}", deletedId);
            return ResponseEntity.ok(deletedId);
        } catch (Exception e) {
            logger.error("Error deleting inventory item with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Search inventory items that need reorder
     * GET /ui/inventory/search/reorder-needed
     */
    @GetMapping("/search/reorder-needed")
    public ResponseEntity<List<InventoryItemSummary>> getItemsNeedingReorder() {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(InventoryItem.ENTITY_NAME);
            modelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            List<EntityWithMetadata<InventoryItem>> allItems = entityService.findAll(modelSpec, InventoryItem.class);
            
            List<InventoryItemSummary> reorderItems = new ArrayList<>();
            for (EntityWithMetadata<InventoryItem> itemWithMetadata : allItems) {
                InventoryItem item = itemWithMetadata.entity();
                if (item.needsReorder()) {
                    InventoryItemSummary summary = new InventoryItemSummary();
                    summary.setTechnicalId(itemWithMetadata.metadata().getId());
                    summary.setProductId(item.getProductId());
                    summary.setSku(item.getSku());
                    summary.setTotalAvailable(item.getTotalAvailableStock());
                    summary.setReorderPoint(item.getReorderPoint());
                    summary.setReorderQuantity(item.getReorderQuantity());
                    summary.setState(itemWithMetadata.metadata().getState());
                    reorderItems.add(summary);
                }
            }

            logger.info("Found {} items needing reorder", reorderItems.size());
            return ResponseEntity.ok(reorderItems);
        } catch (Exception e) {
            logger.error("Error searching for items needing reorder", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search inventory items by SKU
     * GET /ui/inventory/search/sku/{sku}
     */
    @GetMapping("/search/sku/{sku}")
    public ResponseEntity<List<EntityWithMetadata<InventoryItem>>> searchInventoryBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(InventoryItem.ENTITY_NAME);
            modelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition skuCondition = new SimpleCondition()
                    .withJsonPath("$.sku")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(sku));
            conditions.add(skuCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<InventoryItem>> results = entityService.search(modelSpec, groupCondition, InventoryItem.class);
            logger.info("Found {} inventory items for SKU: {}", results.size(), sku);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching inventory by SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get inventory summary for a product (technical ID only) for performance
     * GET /ui/inventory/{id}/summary
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<InventoryItemSummary> getInventoryItemSummary(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(InventoryItem.ENTITY_NAME);
            modelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> response = entityService.getById(id, modelSpec, InventoryItem.class);
            InventoryItem item = response.entity();
            
            InventoryItemSummary summary = new InventoryItemSummary();
            summary.setTechnicalId(response.metadata().getId());
            summary.setProductId(item.getProductId());
            summary.setSku(item.getSku());
            summary.setTotalAvailable(item.getTotalAvailableStock());
            summary.setTotalReserved(item.getTotalReservedStock());
            summary.setReorderPoint(item.getReorderPoint());
            summary.setReorderQuantity(item.getReorderQuantity());
            summary.setState(response.metadata().getState());
            summary.setNeedsReorder(item.needsReorder());
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Error retrieving inventory summary for ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all inventory items (use sparingly)
     * GET /ui/inventory/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<EntityWithMetadata<InventoryItem>>> getAllInventoryItems() {
        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(InventoryItem.ENTITY_NAME);
            modelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            List<EntityWithMetadata<InventoryItem>> results = entityService.findAll(modelSpec, InventoryItem.class);
            logger.info("Retrieved {} inventory items", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving all inventory items", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Inventory item summary DTO for performance-optimized responses
     */
    public static class InventoryItemSummary {
        private UUID technicalId;
        private String productId;
        private String sku;
        private Integer totalAvailable;
        private Integer totalReserved;
        private Integer reorderPoint;
        private Integer reorderQuantity;
        private String state;
        private Boolean needsReorder;

        // Getters and setters
        public UUID getTechnicalId() { return technicalId; }
        public void setTechnicalId(UUID technicalId) { this.technicalId = technicalId; }
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getTotalAvailable() { return totalAvailable; }
        public void setTotalAvailable(Integer totalAvailable) { this.totalAvailable = totalAvailable; }
        public Integer getTotalReserved() { return totalReserved; }
        public void setTotalReserved(Integer totalReserved) { this.totalReserved = totalReserved; }
        public Integer getReorderPoint() { return reorderPoint; }
        public void setReorderPoint(Integer reorderPoint) { this.reorderPoint = reorderPoint; }
        public Integer getReorderQuantity() { return reorderQuantity; }
        public void setReorderQuantity(Integer reorderQuantity) { this.reorderQuantity = reorderQuantity; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public Boolean getNeedsReorder() { return needsReorder; }
        public void setNeedsReorder(Boolean needsReorder) { this.needsReorder = needsReorder; }
    }
}
