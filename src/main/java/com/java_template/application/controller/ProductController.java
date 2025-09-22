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
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Product Controller - REST endpoints for product management
 * 
 * Provides endpoints for:
 * - Product search with filters (category, text search, price range)
 * - Product detail by SKU
 * - Product creation and management
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
     * Search products with filters
     * GET /ui/products?search=&category=&minPrice=&maxPrice=&page=&pageSize=
     */
    @GetMapping
    public ResponseEntity<List<ProductSlimDto>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            // Build search conditions
            List<SimpleCondition> conditions = new ArrayList<>();

            // Free-text search on name OR description (simplified to name only for now)
            if (search != null && !search.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search)));
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
                // No filters - get all products
                products = entityService.findAll(modelSpec, Product.class);
            } else {
                // Apply filters
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(new ArrayList<>(conditions));
                products = entityService.search(modelSpec, condition, Product.class);
            }

            // Convert to slim DTOs for performance
            List<ProductSlimDto> slimProducts = products.stream()
                    .map(this::toSlimDto)
                    .collect(Collectors.toList());

            // Apply pagination (simple in-memory pagination for demo)
            int start = page * pageSize;
            int end = Math.min(start + pageSize, slimProducts.size());
            List<ProductSlimDto> paginatedProducts = start < slimProducts.size() 
                    ? slimProducts.subList(start, end) 
                    : new ArrayList<>();

            logger.info("Found {} products (showing {} from page {})", 
                       slimProducts.size(), paginatedProducts.size(), page);

            return ResponseEntity.ok(paginatedProducts);
        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get product detail by SKU
     * GET /ui/products/{sku}
     */
    @GetMapping("/{sku}")
    public ResponseEntity<EntityWithMetadata<Product>> getProductBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            EntityWithMetadata<Product> product = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (product == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(product);
        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create a new product
     * POST /ui/products
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Product>> createProduct(@RequestBody Product product) {
        try {
            // Set creation timestamp
            LocalDateTime now = LocalDateTime.now();
            if (product.getEvents() == null) {
                product.setEvents(new ArrayList<>());
            }
            
            Product.ProductEvent createdEvent = new Product.ProductEvent();
            createdEvent.setType("ProductCreated");
            createdEvent.setAt(now.toString());
            createdEvent.setPayload(java.util.Map.of("sku", product.getSku()));
            product.getEvents().add(createdEvent);

            EntityWithMetadata<Product> response = entityService.create(product);
            logger.info("Product created with SKU: {}", product.getSku());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating product", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update product
     * PUT /ui/products/{sku}
     */
    @PutMapping("/{sku}")
    public ResponseEntity<EntityWithMetadata<Product>> updateProduct(
            @PathVariable String sku,
            @RequestBody Product product) {
        try {
            // Find existing product
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            EntityWithMetadata<Product> existingProduct = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (existingProduct == null) {
                return ResponseEntity.notFound().build();
            }

            // Ensure SKU matches
            product.setSku(sku);

            EntityWithMetadata<Product> response = entityService.update(
                    existingProduct.metadata().getId(), product, "update_product");
            
            logger.info("Product updated with SKU: {}", sku);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating product with SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Converts Product to slim DTO for list views
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
        if (product.getMedia() != null) {
            product.getMedia().stream()
                    .filter(media -> "image".equals(media.getType()))
                    .findFirst()
                    .ifPresent(media -> dto.setImageUrl(media.getUrl()));
        }
        
        return dto;
    }

    /**
     * Slim DTO for product list views (performance optimization)
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
