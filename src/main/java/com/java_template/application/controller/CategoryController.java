package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.category.version_1.Category;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST controller for Category operations.
 * Provides endpoints for managing categories in the store.
 */
@RestController
@RequestMapping("/category")
public class CategoryController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CategoryController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new category
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<Category>> createCategory(@Valid @RequestBody Category category) {
        logger.info("Creating new category: {}", category.getName());
        
        return entityService.addItem(Category.ENTITY_NAME, Category.ENTITY_VERSION, category)
            .thenCompose(entityId -> entityService.getItem(entityId))
            .thenApply(dataPayload -> {
                try {
                    Category savedCategory = objectMapper.treeToValue(dataPayload.getData(), Category.class);
                    return ResponseEntity.status(HttpStatus.CREATED).body(savedCategory);
                } catch (Exception e) {
                    logger.error("Error converting saved category data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error creating category", throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Category>build();
            });
    }

    /**
     * Get all categories
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<List<Category>>> getAllCategories() {
        logger.info("Getting all categories");
        
        return entityService.getItems(Category.ENTITY_NAME, Category.ENTITY_VERSION, null, null, null)
            .thenApply(dataPayloads -> {
                try {
                    List<Category> categories = dataPayloads.stream()
                        .map(dataPayload -> {
                            try {
                                return objectMapper.treeToValue(dataPayload.getData(), Category.class);
                            } catch (Exception e) {
                                logger.warn("Error converting category data", e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                    
                    return ResponseEntity.ok(categories);
                } catch (Exception e) {
                    logger.error("Error getting all categories", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error getting all categories", throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    /**
     * Get category by ID
     */
    @GetMapping("/{categoryId}")
    public CompletableFuture<ResponseEntity<Category>> getCategoryById(@PathVariable Long categoryId) {
        logger.info("Getting category by ID: {}", categoryId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(categoryId.toString().getBytes());
        
        return entityService.getItem(entityId)
            .thenApply(dataPayload -> {
                try {
                    Category category = objectMapper.treeToValue(dataPayload.getData(), Category.class);
                    return ResponseEntity.ok(category);
                } catch (Exception e) {
                    logger.error("Error converting category data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error getting category by ID", throwable);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }

    /**
     * Update category
     */
    @PutMapping("/{categoryId}")
    public CompletableFuture<ResponseEntity<Category>> updateCategory(
            @PathVariable Long categoryId, 
            @Valid @RequestBody Category category) {
        logger.info("Updating category with ID: {}", categoryId);
        
        // Set the ID from path parameter
        category.setId(categoryId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(categoryId.toString().getBytes());
        
        return entityService.updateItem(entityId, category)
            .thenCompose(updatedId -> entityService.getItem(updatedId))
            .thenApply(dataPayload -> {
                try {
                    Category updatedCategory = objectMapper.treeToValue(dataPayload.getData(), Category.class);
                    return ResponseEntity.ok(updatedCategory);
                } catch (Exception e) {
                    logger.error("Error converting updated category data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error updating category", throwable);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }

    /**
     * Delete category
     */
    @DeleteMapping("/{categoryId}")
    public CompletableFuture<ResponseEntity<Void>> deleteCategory(@PathVariable Long categoryId) {
        logger.info("Deleting category by ID: {}", categoryId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(categoryId.toString().getBytes());
        
        return entityService.deleteItem(entityId)
            .thenApply(deletedId -> ResponseEntity.ok().<Void>build())
            .exceptionally(throwable -> {
                logger.error("Error deleting category", throwable);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }
}
