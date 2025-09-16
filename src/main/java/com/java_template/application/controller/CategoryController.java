package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.category.version_1.Category;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CategoryController - Manage pet categories
 * 
 * Base Path: /api/categories
 * Entity: Category
 * Purpose: Manage pet categories
 */
@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CategoryController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get all categories with optional filtering
     * GET /api/categories
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Category>>> getAllCategories(
            @RequestParam(required = false) String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Category.ENTITY_NAME).withVersion(Category.ENTITY_VERSION);
            List<EntityWithMetadata<Category>> categories = entityService.findAll(modelSpec, Category.class);
            
            // Filter by status if provided (status is in metadata)
            if (status != null) {
                categories = categories.stream()
                        .filter(category -> status.equals(category.metadata().getState()))
                        .collect(Collectors.toList());
            }

            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            logger.error("Error getting categories", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get category by technical UUID
     * GET /api/categories/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Category>> getCategoryById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Category.ENTITY_NAME).withVersion(Category.ENTITY_VERSION);
            EntityWithMetadata<Category> response = entityService.getById(id, modelSpec, Category.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting category by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get category by business identifier
     * GET /api/categories/business/{categoryId}
     */
    @GetMapping("/business/{categoryId}")
    public ResponseEntity<EntityWithMetadata<Category>> getCategoryByBusinessId(@PathVariable String categoryId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Category.ENTITY_NAME).withVersion(Category.ENTITY_VERSION);
            EntityWithMetadata<Category> response = entityService.findByBusinessId(
                    modelSpec, categoryId, "categoryId", Category.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting category by business ID: {}", categoryId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create new category
     * POST /api/categories
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Category>> createCategory(@RequestBody Category category) {
        try {
            // Set creation timestamp
            category.setCreatedAt(LocalDateTime.now());
            category.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Category> response = entityService.create(category);
            logger.info("Category created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating category", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update category with optional workflow transition
     * PUT /api/categories/{id}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Category>> updateCategory(
            @PathVariable UUID id,
            @RequestBody Category category,
            @RequestParam(required = false) String transitionName) {
        try {
            // Set update timestamp
            category.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Category> response = entityService.update(id, category, transitionName);
            logger.info("Category updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating category", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deactivate category
     * PUT /api/categories/{categoryId}/deactivate
     */
    @PutMapping("/{categoryId}/deactivate")
    public ResponseEntity<EntityWithMetadata<Category>> deactivateCategory(@PathVariable String categoryId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Category.ENTITY_NAME).withVersion(Category.ENTITY_VERSION);
            EntityWithMetadata<Category> categoryEntity = entityService.findByBusinessId(
                    modelSpec, categoryId, "categoryId", Category.class);

            if (categoryEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Category> response = entityService.update(
                    categoryEntity.metadata().getId(), categoryEntity.entity(), "deactivate_category");
            logger.info("Category {} deactivated", categoryId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deactivating category: {}", categoryId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Activate category
     * PUT /api/categories/{categoryId}/activate
     */
    @PutMapping("/{categoryId}/activate")
    public ResponseEntity<EntityWithMetadata<Category>> activateCategory(@PathVariable String categoryId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Category.ENTITY_NAME).withVersion(Category.ENTITY_VERSION);
            EntityWithMetadata<Category> categoryEntity = entityService.findByBusinessId(
                    modelSpec, categoryId, "categoryId", Category.class);

            if (categoryEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Category> response = entityService.update(
                    categoryEntity.metadata().getId(), categoryEntity.entity(), "reactivate_category");
            logger.info("Category {} activated", categoryId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating category: {}", categoryId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete category
     * DELETE /api/categories/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Category deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting category", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
