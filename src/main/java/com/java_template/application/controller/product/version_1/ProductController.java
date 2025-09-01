package com.java_template.application.controller.product.version_1;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.dto.EntityListResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        logger.info("Creating product with SKU: {}", product.getSku());

        try {
            Product createdProduct = entityService.create(product);
            logger.info("Product created successfully with SKU: {}", createdProduct.getSku());
            return ResponseEntity.ok(createdProduct);
        } catch (Exception e) {
            logger.error("Failed to create product: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProduct(@PathVariable String sku) {
        logger.info("Retrieving product with SKU: {}", sku);

        try {
            Product product = entityService.findById(Product.class, sku);
            if (product != null) {
                logger.info("Product found with SKU: {}", sku);
                return ResponseEntity.ok(product);
            } else {
                logger.warn("Product not found with SKU: {}", sku);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve product: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        logger.info("Retrieving all products");

        try {
            List<Product> products = entityService.findAll(Product.class);
            logger.info("Retrieved {} products", products.size());
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Failed to retrieve products: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{sku}")
    public ResponseEntity<Product> updateProduct(@PathVariable String sku, @RequestBody Product product) {
        logger.info("Updating product with SKU: {}", sku);

        // Ensure the SKU in the path matches the product SKU
        product.setSku(sku);

        try {
            Product updatedProduct = entityService.update(product);
            logger.info("Product updated successfully with SKU: {}", sku);
            return ResponseEntity.ok(updatedProduct);
        } catch (Exception e) {
            logger.error("Failed to update product: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String sku) {
        logger.info("Deleting product with SKU: {}", sku);

        try {
            boolean deleted = entityService.delete(Product.class, sku);
            if (deleted) {
                logger.info("Product deleted successfully with SKU: {}", sku);
                return ResponseEntity.noContent().build();
            } else {
                logger.warn("Product not found for deletion with SKU: {}", sku);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to delete product: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable String category) {
        logger.info("Retrieving products by category: {}", category);

        try {
            List<Product> products = entityService.findByField(Product.class, "category", category);
            logger.info("Retrieved {} products for category: {}", products.size(), category);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Failed to retrieve products by category: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Enhanced endpoints that return both data and metadata

    @PostMapping("/with-metadata")
    public ResponseEntity<EntityResponse<Product>> createProductWithMetadata(@RequestBody Product product) {
        logger.info("Creating product with SKU: {} (with metadata)", product.getSku());

        try {
            EntityResponse<Product> entityResponse = entityService.createWithMetadata(product);
            logger.info("Product created successfully with SKU: {} and technical ID: {}",
                entityResponse.getData().getSku(), entityResponse.getId());
            return ResponseEntity.ok(entityResponse);
        } catch (Exception e) {
            logger.error("Failed to create product: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{sku}/with-metadata")
    public ResponseEntity<EntityResponse<Product>> getProductWithMetadata(@PathVariable String sku) {
        logger.info("Retrieving product with SKU: {} (with metadata)", sku);

        try {
            EntityResponse<Product> entityResponse = entityService.findByIdWithMetadata(Product.class, sku);
            if (entityResponse != null) {
                logger.info("Product found with SKU: {} and technical ID: {}", sku, entityResponse.getId());
                return ResponseEntity.ok(entityResponse);
            } else {
                logger.warn("Product not found with SKU: {}", sku);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve product: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/with-metadata")
    public ResponseEntity<EntityListResponse<Product>> getAllProductsWithMetadata() {
        logger.info("Retrieving all products (with metadata)");

        try {
            EntityListResponse<Product> entityListResponse = entityService.findAllWithMetadata(Product.class);
            logger.info("Retrieved {} products with metadata", entityListResponse.getTotalCount());
            return ResponseEntity.ok(entityListResponse);
        } catch (Exception e) {
            logger.error("Failed to retrieve products: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{sku}/with-metadata")
    public ResponseEntity<EntityResponse<Product>> updateProductWithMetadata(@PathVariable String sku, @RequestBody Product product) {
        logger.info("Updating product with SKU: {} (with metadata)", sku);

        // Ensure the SKU in the path matches the product SKU
        product.setSku(sku);

        try {
            EntityResponse<Product> entityResponse = entityService.updateWithMetadata(product);
            logger.info("Product updated successfully with SKU: {} and technical ID: {}", sku, entityResponse.getId());
            return ResponseEntity.ok(entityResponse);
        } catch (Exception e) {
            logger.error("Failed to update product: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/category/{category}/with-metadata")
    public ResponseEntity<EntityListResponse<Product>> getProductsByCategoryWithMetadata(@PathVariable String category) {
        logger.info("Retrieving products by category: {} (with metadata)", category);

        try {
            EntityListResponse<Product> entityListResponse = entityService.findByFieldWithMetadata(Product.class, "category", category);
            logger.info("Retrieved {} products for category: {} with metadata", entityListResponse.getTotalCount(), category);
            return ResponseEntity.ok(entityListResponse);
        } catch (Exception e) {
            logger.error("Failed to retrieve products by category: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}