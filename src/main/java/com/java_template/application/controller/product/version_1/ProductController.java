package com.java_template.application.controller.product.version_1;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public CompletableFuture<ResponseEntity<Product>> createProduct(@RequestBody Product product) {
        logger.info("Creating product with SKU: {}", product.getSku());

        return entityService.create(product)
            .thenApply(createdProduct -> {
                logger.info("Product created successfully with SKU: {}", createdProduct.getSku());
                return ResponseEntity.ok(createdProduct);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to create product: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/{sku}")
    public CompletableFuture<ResponseEntity<Product>> getProduct(@PathVariable String sku) {
        logger.info("Retrieving product with SKU: {}", sku);

        return entityService.findById(Product.class, sku)
            .thenApply(product -> {
                if (product != null) {
                    logger.info("Product found with SKU: {}", sku);
                    return ResponseEntity.ok(product);
                } else {
                    logger.warn("Product not found with SKU: {}", sku);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve product: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<Product>>> getAllProducts() {
        logger.info("Retrieving all products");

        return entityService.findAll(Product.class)
            .thenApply(products -> {
                logger.info("Retrieved {} products", products.size());
                return ResponseEntity.ok(products);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve products: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PutMapping("/{sku}")
    public CompletableFuture<ResponseEntity<Product>> updateProduct(@PathVariable String sku, @RequestBody Product product) {
        logger.info("Updating product with SKU: {}", sku);

        // Ensure the SKU in the path matches the product SKU
        product.setSku(sku);

        return entityService.update(product)
            .thenApply(updatedProduct -> {
                logger.info("Product updated successfully with SKU: {}", sku);
                return ResponseEntity.ok(updatedProduct);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to update product: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @DeleteMapping("/{sku}")
    public CompletableFuture<ResponseEntity<Void>> deleteProduct(@PathVariable String sku) {
        logger.info("Deleting product with SKU: {}", sku);

        return entityService.delete(Product.class, sku)
            .thenApply(deleted -> {
                if (deleted) {
                    logger.info("Product deleted successfully with SKU: {}", sku);
                    return ResponseEntity.noContent().build();
                } else {
                    logger.warn("Product not found for deletion with SKU: {}", sku);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to delete product: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/category/{category}")
    public CompletableFuture<ResponseEntity<List<Product>>> getProductsByCategory(@PathVariable String category) {
        logger.info("Retrieving products by category: {}", category);

        return entityService.findByField(Product.class, "category", category)
            .thenApply(products -> {
                logger.info("Retrieved {} products for category: {}", products.size(), category);
                return ResponseEntity.ok(products);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve products by category: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
}