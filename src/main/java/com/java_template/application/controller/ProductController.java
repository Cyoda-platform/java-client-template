package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Product>> createProduct(@RequestBody Product product) {
        try {
            EntityResponse<Product> response = entityService.save(product);
            logger.info("Product created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating product", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Product>> getProduct(@PathVariable UUID id) {
        try {
            EntityResponse<Product> response = entityService.getItem(id, Product.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving product with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<EntityResponse<Product>> getProductBySku(@PathVariable String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(skuCondition));

            Optional<EntityResponse<Product>> response = entityService.getFirstItemByCondition(
                Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving product with SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Product>>> getAllProducts() {
        try {
            List<EntityResponse<Product>> products = entityService.findAll(Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Error retrieving all products", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Product>> updateProduct(
            @PathVariable UUID id, 
            @RequestBody Product product,
            @RequestParam(required = false) String transition) {
        try {
            EntityResponse<Product> response = entityService.update(id, product, transition);
            logger.info("Product updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating product with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Product deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting product with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<EntityResponse<Product>> activateProduct(@PathVariable UUID id) {
        try {
            EntityResponse<Product> productResponse = entityService.getItem(id, Product.class);
            Product product = productResponse.getData();
            EntityResponse<Product> response = entityService.update(id, product, "ACTIVATE");
            logger.info("Product activated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating product with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/discontinue")
    public ResponseEntity<EntityResponse<Product>> discontinueProduct(@PathVariable UUID id) {
        try {
            EntityResponse<Product> productResponse = entityService.getItem(id, Product.class);
            Product product = productResponse.getData();
            EntityResponse<Product> response = entityService.update(id, product, "DISCONTINUE");
            logger.info("Product discontinued with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error discontinuing product with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
