package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Product controller providing catalog search and management endpoints
 * with support for category filtering, free-text search, price range, and pagination.
 */
@RestController
@RequestMapping("/ui/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProductController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new product
     * POST /ui/products
     */
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        try {
            // Check for duplicate SKU
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, product.getSku(), "sku", Product.class);

            if (existing != null) {
                logger.warn("Product with SKU {} already exists", product.getSku());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format(
                        "Product already exists with SKU '%s'. Technical ID: %s",
                        product.getSku(),
                        existing.getId()
                    )
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Product> response = entityService.create(product);
            logger.info("Product created with SKU: {}", response.entity().getSku());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Product", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get product by technical UUID (FASTEST method)
     * GET /ui/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Product>> getProductById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> response = entityService.getById(id, modelSpec, Product.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Product by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get product by SKU (business identifier)
     * GET /ui/products/sku/{sku}
     */
    @GetMapping("/sku/{sku}")
    public ResponseEntity<EntityWithMetadata<Product>> getProductBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> response = entityService.findByBusinessIdOrNull(
                    modelSpec, sku, "sku", Product.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Product by SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update product with optional workflow transition
     * PUT /ui/products/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Product>> updateProduct(
            @PathVariable UUID id,
            @RequestBody Product product,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Product> response = entityService.update(id, product, transition);
            logger.info("Product updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Product", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete product by technical UUID
     * DELETE /ui/products/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Product deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Product", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search products with filters
     * GET /ui/products?search=&category=&minPrice=&maxPrice=&page=&pageSize=
     */
    @GetMapping
    public ResponseEntity<List<ProductSlimDto>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);

            // Build search conditions
            List<QueryCondition> conditions = new ArrayList<>();

            // Free-text search on name OR description
            if (search != null && !search.trim().isEmpty()) {
                // Create OR condition for name and description
                List<QueryCondition> textConditions = new ArrayList<>();
                
                textConditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search)));
                
                textConditions.add(new SimpleCondition()
                        .withJsonPath("$.description")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search)));

                GroupCondition textGroup = new GroupCondition()
                        .withOperator(GroupCondition.Operator.OR)
                        .withConditions(textConditions);
                
                conditions.add(textGroup);
            }

            // Category filter
            if (category != null && !category.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.category")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(category)));
            }

            // Price range filters
            if (minPrice != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(minPrice)));
            }

            if (maxPrice != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(maxPrice)));
            }

            // Execute search
            List<EntityWithMetadata<Product>> products;
            if (conditions.isEmpty()) {
                // No filters - get all products (use sparingly)
                products = entityService.findAll(modelSpec, Product.class);
            } else {
                // Apply filters
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                products = entityService.search(modelSpec, condition, Product.class);
            }

            // Convert to slim DTOs for performance
            List<ProductSlimDto> slimProducts = products.stream()
                    .map(this::toSlimDto)
                    .toList();

            // Simple pagination (in-memory for demo - in production use database pagination)
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, slimProducts.size());
            
            if (start >= slimProducts.size()) {
                return ResponseEntity.ok(List.of());
            }

            List<ProductSlimDto> paginatedResults = slimProducts.subList(start, end);
            return ResponseEntity.ok(paginatedResults);

        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Convert full Product entity to slim DTO for list views
     */
    private ProductSlimDto toSlimDto(EntityWithMetadata<Product> productWithMetadata) {
        Product product = productWithMetadata.entity();
        ProductSlimDto dto = new ProductSlimDto();
        dto.setSku(product.getSku());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setQuantityAvailable(product.getQuantityAvailable());
        dto.setCategory(product.getCategory());
        
        // Extract image URL from media if available
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            product.getMedia().stream()
                    .filter(media -> "image".equals(media.getType()))
                    .findFirst()
                    .ifPresent(media -> dto.setImageUrl(media.getUrl()));
        }
        
        return dto;
    }

    /**
     * Slim DTO for product list views (performance optimized)
     */
    @Getter
    @Setter
    public static class ProductSlimDto {
        private String sku;
        private String name;
        private String description;
        private Double price;
        private Integer quantityAvailable;
        private String category;
        private String imageUrl;
    }
}
