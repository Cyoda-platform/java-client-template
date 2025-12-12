package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ProductController
 * 
 * REST endpoints for product management:
 * - POST /ui/product - Create new product
 * - GET /ui/product/{id} - Get product by technical ID
 * - GET /ui/product - List all products with pagination
 * - PUT /ui/product/{id} - Update product
 * - DELETE /ui/product/{id} - Delete product
 */
@RestController
@RequestMapping("/ui/product")
@CrossOrigin(origins = "*")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new product
     * POST /ui/product
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Product>> createProduct(@Valid @RequestBody Product product) {
        try {
            // Set creation timestamp
            product.setCreatedAt(LocalDateTime.now());
            product.setUpdatedAt(LocalDateTime.now());

            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);
            
            EntityWithMetadata<Product> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, product.getProductId(), "productId", Product.class);

            if (existing != null) {
                logger.warn("Product with ID {} already exists", product.getProductId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Product already exists with ID: %s", product.getProductId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Product> response = entityService.create(product);
            logger.info("Product created with ID: {}", response.metadata().getId());

            // Build Location header
            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create product", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create product: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get product by technical UUID
     * GET /ui/product/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Product>> getProductById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);
            
            EntityWithMetadata<Product> response = entityService.getById(id, modelSpec, Product.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve product with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve product with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all products with pagination
     * GET /ui/product?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Product>>> listProducts(Pageable pageable) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);
            
            Page<EntityWithMetadata<Product>> response = entityService.findAll(modelSpec, pageable, Product.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to list products", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list products: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update product
     * PUT /ui/product/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Product>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody Product product) {
        try {
            product.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Product> response = entityService.update(id, product, null);
            logger.info("Product updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update product with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update product with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete product by technical UUID
     * DELETE /ui/product/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Product deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete product with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete product with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

