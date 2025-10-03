package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for InventoryItem management
 * Provides CRUD operations, stock adjustments, and inventory queries
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
            // Set creation timestamp
            inventoryItem.setCreatedAt(LocalDateTime.now());
            inventoryItem.setUpdatedAt(LocalDateTime.now());
            inventoryItem.setLastUpdatedBy("InventoryItemController");

            EntityWithMetadata<InventoryItem> response = entityService.create(inventoryItem);
            logger.info("InventoryItem created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating inventory item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get inventory item by technical UUID
     * GET /ui/inventory/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> getInventoryItemById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);
            EntityWithMetadata<InventoryItem> response = entityService.getById(id, modelSpec, InventoryItem.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting inventory item by ID: {}", id, e);
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
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);
            EntityWithMetadata<InventoryItem> response = entityService.findByBusinessId(
                    modelSpec, productId, "productId", InventoryItem.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting inventory item by product ID: {}", productId, e);
            return ResponseEntity.badRequest().build();
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
            inventoryItem.setUpdatedAt(LocalDateTime.now());
            inventoryItem.setLastUpdatedBy("InventoryItemController");

            EntityWithMetadata<InventoryItem> response = entityService.update(id, inventoryItem, transition);
            logger.info("InventoryItem updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating inventory item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Adjust stock for inventory item
     * POST /ui/inventory/{id}/adjust-stock
     */
    @PostMapping("/{id}/adjust-stock")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> adjustStock(
            @PathVariable UUID id,
            @RequestBody StockAdjustmentRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);
            EntityWithMetadata<InventoryItem> inventoryResponse = entityService.getById(id, modelSpec, InventoryItem.class);
            
            InventoryItem inventoryItem = inventoryResponse.entity();
            inventoryItem.setUpdatedAt(LocalDateTime.now());
            inventoryItem.setLastUpdatedBy("InventoryItemController");

            EntityWithMetadata<InventoryItem> response = entityService.update(id, inventoryItem, "adjust_stock");
            logger.info("Stock adjusted for inventory item ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adjusting stock", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reserve stock for inventory item
     * POST /ui/inventory/{id}/reserve-stock
     */
    @PostMapping("/{id}/reserve-stock")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> reserveStock(
            @PathVariable UUID id,
            @RequestBody StockReservationRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);
            EntityWithMetadata<InventoryItem> inventoryResponse = entityService.getById(id, modelSpec, InventoryItem.class);
            
            InventoryItem inventoryItem = inventoryResponse.entity();
            inventoryItem.setUpdatedAt(LocalDateTime.now());
            inventoryItem.setLastUpdatedBy("InventoryItemController");

            EntityWithMetadata<InventoryItem> response = entityService.update(id, inventoryItem, "reserve_stock");
            logger.info("Stock reserved for inventory item ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reserving stock", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Check reorder status for inventory item
     * POST /ui/inventory/{id}/check-reorder
     */
    @PostMapping("/{id}/check-reorder")
    public ResponseEntity<EntityWithMetadata<InventoryItem>> checkReorder(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);
            EntityWithMetadata<InventoryItem> inventoryResponse = entityService.getById(id, modelSpec, InventoryItem.class);
            
            InventoryItem inventoryItem = inventoryResponse.entity();
            inventoryItem.setUpdatedAt(LocalDateTime.now());
            inventoryItem.setLastUpdatedBy("InventoryItemController");

            EntityWithMetadata<InventoryItem> response = entityService.update(id, inventoryItem, "check_reorder");
            logger.info("Reorder check performed for inventory item ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking reorder", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all inventory items
     * GET /ui/inventory
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<InventoryItem>>> getAllInventoryItems() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);
            List<EntityWithMetadata<InventoryItem>> items = entityService.findAll(modelSpec, InventoryItem.class);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error getting all inventory items", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search inventory items by SKU
     * GET /ui/inventory/search?sku=ABC123
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<InventoryItem>>> searchInventoryItemsBySku(
            @RequestParam String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.sku")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(sku));
            conditions.add(condition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<InventoryItem>> items = entityService.search(modelSpec, groupCondition, InventoryItem.class);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error searching inventory items by SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get low stock items (items needing reorder)
     * GET /ui/inventory/low-stock
     */
    @GetMapping("/low-stock")
    public ResponseEntity<List<EntityWithMetadata<InventoryItem>>> getLowStockItems() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(InventoryItem.ENTITY_NAME).withVersion(InventoryItem.ENTITY_VERSION);
            List<EntityWithMetadata<InventoryItem>> allItems = entityService.findAll(modelSpec, InventoryItem.class);
            
            // Filter items that need reorder
            List<EntityWithMetadata<InventoryItem>> lowStockItems = allItems.stream()
                    .filter(item -> item.entity().isReorderNeeded())
                    .toList();
            
            return ResponseEntity.ok(lowStockItems);
        } catch (Exception e) {
            logger.error("Error getting low stock items", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete inventory item by technical UUID
     * DELETE /ui/inventory/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInventoryItem(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("InventoryItem deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting inventory item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for stock adjustment requests
     */
    @Getter
    @Setter
    public static class StockAdjustmentRequest {
        private String locationId;
        private String stockType; // available, reserved, damaged
        private Integer adjustment;
        private String reason;
    }

    /**
     * DTO for stock reservation requests
     */
    @Getter
    @Setter
    public static class StockReservationRequest {
        private Integer quantity;
        private String referenceId;
        private String reason;
    }
}
