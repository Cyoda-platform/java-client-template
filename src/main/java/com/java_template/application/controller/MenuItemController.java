package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.menuitem.version_1.MenuItem;
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
 * MenuItem Controller - Manages menu item entities and workflow transitions
 * Provides CRUD operations and workflow state management for menu items
 */
@RestController
@RequestMapping("/api/menu-items")
@CrossOrigin(origins = "*")
public class MenuItemController {

    private static final Logger logger = LoggerFactory.getLogger(MenuItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MenuItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new menu item
     * POST /api/menu-items
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<MenuItem>> createMenuItem(@RequestBody MenuItem menuItem) {
        try {
            menuItem.setCreatedAt(LocalDateTime.now());
            menuItem.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<MenuItem> response = entityService.create(menuItem);
            logger.info("MenuItem created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating menu item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get menu item by technical UUID
     * GET /api/menu-items/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<MenuItem>> getMenuItemById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MenuItem.ENTITY_NAME).withVersion(MenuItem.ENTITY_VERSION);
            EntityWithMetadata<MenuItem> response = entityService.getById(id, modelSpec, MenuItem.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting menu item by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get menu item by business identifier
     * GET /api/menu-items/business/{menuItemId}
     */
    @GetMapping("/business/{menuItemId}")
    public ResponseEntity<EntityWithMetadata<MenuItem>> getMenuItemByBusinessId(@PathVariable String menuItemId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MenuItem.ENTITY_NAME).withVersion(MenuItem.ENTITY_VERSION);
            EntityWithMetadata<MenuItem> response = entityService.findByBusinessId(
                    modelSpec, menuItemId, "menuItemId", MenuItem.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting menu item by business ID: {}", menuItemId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update menu item with optional workflow transition
     * PUT /api/menu-items/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<MenuItem>> updateMenuItem(
            @PathVariable UUID id,
            @RequestBody MenuItem menuItem,
            @RequestParam(required = false) String transition) {
        try {
            menuItem.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<MenuItem> response = entityService.update(id, menuItem, transition);
            logger.info("MenuItem updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating menu item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete menu item by technical UUID
     * DELETE /api/menu-items/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("MenuItem deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting menu item", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all menu items
     * GET /api/menu-items
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<MenuItem>>> getAllMenuItems() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MenuItem.ENTITY_NAME).withVersion(MenuItem.ENTITY_VERSION);
            List<EntityWithMetadata<MenuItem>> menuItems = entityService.findAll(modelSpec, MenuItem.class);
            return ResponseEntity.ok(menuItems);
        } catch (Exception e) {
            logger.error("Error getting all menu items", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get menu items by restaurant
     * GET /api/menu-items/restaurant/{restaurantId}
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<EntityWithMetadata<MenuItem>>> getMenuItemsByRestaurant(@PathVariable String restaurantId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MenuItem.ENTITY_NAME).withVersion(MenuItem.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<MenuItem>> menuItems = entityService.search(modelSpec, condition, MenuItem.class);
            return ResponseEntity.ok(menuItems);
        } catch (Exception e) {
            logger.error("Error getting menu items by restaurant: {}", restaurantId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search menu items by category
     * GET /api/menu-items/search?category=categoryName
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<MenuItem>>> searchMenuItemsByCategory(
            @RequestParam String category) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MenuItem.ENTITY_NAME).withVersion(MenuItem.ENTITY_VERSION);

            SimpleCondition categoryCondition = new SimpleCondition()
                    .withJsonPath("$.category")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(category));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(categoryCondition));

            List<EntityWithMetadata<MenuItem>> menuItems = entityService.search(modelSpec, condition, MenuItem.class);
            return ResponseEntity.ok(menuItems);
        } catch (Exception e) {
            logger.error("Error searching menu items by category: {}", category, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for menu items
     * POST /api/menu-items/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<MenuItem>>> advancedSearch(
            @RequestBody MenuItemSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(MenuItem.ENTITY_NAME).withVersion(MenuItem.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getCategory() != null && !searchRequest.getCategory().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.category")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCategory())));
            }

            if (searchRequest.getRestaurantId() != null && !searchRequest.getRestaurantId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.restaurantId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getRestaurantId())));
            }

            if (searchRequest.getIsAvailable() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isAvailable")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsAvailable())));
            }

            if (searchRequest.getMinPrice() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinPrice())));
            }

            if (searchRequest.getMaxPrice() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxPrice())));
            }

            if (searchRequest.getIsVegetarian() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isVegetarian")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsVegetarian())));
            }

            if (searchRequest.getIsVegan() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isVegan")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsVegan())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<MenuItem>> menuItems = entityService.search(modelSpec, condition, MenuItem.class);
            return ResponseEntity.ok(menuItems);
        } catch (Exception e) {
            logger.error("Error performing advanced menu item search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced menu item search requests
     */
    @Getter
    @Setter
    public static class MenuItemSearchRequest {
        private String name;
        private String category;
        private String restaurantId;
        private Boolean isAvailable;
        private Double minPrice;
        private Double maxPrice;
        private Boolean isVegetarian;
        private Boolean isVegan;
    }
}
