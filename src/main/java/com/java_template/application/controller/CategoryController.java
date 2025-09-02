package com.java_template.application.controller;

import com.java_template.application.entity.category.version_1.Category;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    @Autowired
    private EntityService entityService;

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories(
            @RequestParam(required = false) Boolean active) {
        
        try {
            List<EntityResponse<Category>> categoryResponses = entityService.findAll(
                Category.class, 
                Category.ENTITY_NAME, 
                Category.ENTITY_VERSION
            );
            
            List<Category> categories = categoryResponses.stream()
                .map(EntityResponse::getData)
                .toList();
            
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable UUID id) {
        try {
            EntityResponse<Category> categoryResponse = entityService.getItem(id, Category.class);
            return ResponseEntity.ok(categoryResponse.getData());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody Category category) {
        try {
            EntityResponse<Category> savedCategory = entityService.save(category);
            
            Map<String, Object> response = Map.of(
                "id", savedCategory.getMetadata().getId(),
                "name", savedCategory.getData().getName(),
                "state", savedCategory.getMetadata().getState(),
                "message", "Category created and activated successfully"
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable UUID id,
            @RequestBody Category category,
            @RequestParam(required = false) String transition) {
        
        try {
            EntityResponse<Category> updatedCategory = entityService.update(id, category, transition);
            
            Map<String, Object> response = Map.of(
                "id", updatedCategory.getMetadata().getId(),
                "name", updatedCategory.getData().getName(),
                "state", updatedCategory.getMetadata().getState(),
                "message", transition != null ? 
                    "Category updated with transition: " + transition : 
                    "Category updated successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable UUID id) {
        try {
            // Get the category first to update it with archive transition
            EntityResponse<Category> categoryResponse = entityService.getItem(id, Category.class);
            Category category = categoryResponse.getData();
            
            EntityResponse<Category> archivedCategory = entityService.update(id, category, "archive_category");
            
            Map<String, Object> response = Map.of(
                "id", archivedCategory.getMetadata().getId(),
                "message", "Category archived successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
