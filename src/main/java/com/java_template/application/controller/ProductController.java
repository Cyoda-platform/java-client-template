package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<EntityResponse<Product>> createProduct(@RequestBody Product product) {
        try {
            logger.info("Creating new product: {}", product.getName());
            EntityResponse<Product> response = entityService.save(product);
            logger.info("Product created with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to create product: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Product>> getProduct(@PathVariable UUID id) {
        try {
            logger.info("Retrieving product with ID: {}", id);
            EntityResponse<Product> response = entityService.getItem(id, Product.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve product {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Product>>> getAllProducts() {
        try {
            logger.info("Retrieving all products");
            List<EntityResponse<Product>> products = entityService.getItems(
                Product.class,
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                null,
                null,
                null
            );
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Failed to retrieve products: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<EntityResponse<Product>>> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String state) {
        try {
            logger.info("Searching products with filters - name: {}, category: {}, state: {}", name, category, state);
            
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            
            List<Condition> conditions = new java.util.ArrayList<>();
            
            if (name != null && !name.trim().isEmpty()) {
                conditions.add(Condition.of("$.name", "CONTAINS", name));
            }
            if (category != null && !category.trim().isEmpty()) {
                conditions.add(Condition.of("$.category", "EQUALS", category));
            }
            if (state != null && !state.trim().isEmpty()) {
                conditions.add(Condition.lifecycle("state", "EQUALS", state));
            }
            
            condition.setConditions(conditions);
            
            List<EntityResponse<Product>> products = entityService.getItemsByCondition(
                Product.class,
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                condition,
                true
            );
            
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Failed to search products: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Product>> updateProduct(
            @PathVariable UUID id, 
            @RequestBody Product product,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating product with ID: {}, transition: {}", id, transition);
            
            EntityResponse<Product> response = entityService.update(id, product, transition);
            
            logger.info("Product updated with ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update product {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        try {
            logger.info("Deleting product with ID: {}", id);
            entityService.deleteById(id);
            logger.info("Product deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete product {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/transitions/{transitionName}")
    public ResponseEntity<EntityResponse<Product>> transitionProduct(
            @PathVariable UUID id, 
            @PathVariable String transitionName) {
        try {
            logger.info("Transitioning product {} with transition: {}", id, transitionName);
            
            // Get current product
            EntityResponse<Product> currentResponse = entityService.getItem(id, Product.class);
            Product product = currentResponse.getData();
            
            // Update with transition
            EntityResponse<Product> response = entityService.update(id, product, transitionName);
            
            logger.info("Product transitioned with ID: {}, new state: {}", response.getId(), response.getState());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to transition product {} with {}: {}", id, transitionName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<Product>> getProductByBusinessId(@PathVariable Long businessId) {
        try {
            logger.info("Retrieving product with business ID: {}", businessId);
            EntityResponse<Product> response = entityService.findByBusinessId(
                Product.class,
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                businessId.toString(),
                "id"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve product by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<Product>> updateProductByBusinessId(
            @PathVariable Long businessId, 
            @RequestBody Product product,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating product with business ID: {}, transition: {}", businessId, transition);
            
            EntityResponse<Product> response = entityService.updateByBusinessId(product, "id", transition);
            
            logger.info("Product updated with business ID: {}", businessId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update product by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
