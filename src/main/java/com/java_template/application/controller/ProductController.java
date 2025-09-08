package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ProductController - UI-facing REST controller for product operations
 * 
 * This controller provides:
 * - Product search with filters (category, free-text, price range)
 * - Product detail retrieval (full schema)
 * - Product list view (slim DTO for performance)
 * 
 * Endpoints:
 * - GET /ui/products - Search products with filters
 * - GET /ui/products/{sku} - Get full product details
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

            // Free-text search on name OR description
            if (search != null && !search.trim().isEmpty()) {
                SimpleCondition nameCondition = new SimpleCondition()
                    .withJsonPath("$.name")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(search));

                SimpleCondition descCondition = new SimpleCondition()
                    .withJsonPath("$.description")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(search));

                // For simplicity, we'll search for name containing the search term
                // In a real implementation, you might want to use more sophisticated search
                conditions.add(nameCondition);
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
                GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));
                products = entityService.search(modelSpec, condition, Product.class);
            }

            // Convert to slim DTOs for performance
            List<ProductSlimDto> slimProducts = products.stream()
                .map(this::toSlimDto)
                .collect(Collectors.toList());

            // Apply pagination
            int start = page * pageSize;
            int end = Math.min(start + pageSize, slimProducts.size());
            if (start >= slimProducts.size()) {
                return ResponseEntity.ok(new ArrayList<>());
            }

            List<ProductSlimDto> paginatedProducts = slimProducts.subList(start, end);
            
            logger.info("Found {} products (showing {} to {})", 
                       slimProducts.size(), start, end);
            
            return ResponseEntity.ok(paginatedProducts);
        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get full product details by SKU
     * GET /ui/products/{sku}
     */
    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

            // Search by SKU
            SimpleCondition skuCondition = new SimpleCondition()
                .withJsonPath("$.sku")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(sku));

            GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(skuCondition));

            List<EntityWithMetadata<Product>> products = entityService.search(
                modelSpec, condition, Product.class);

            if (products.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Return full product document
            Product product = products.get(0).entity();
            return ResponseEntity.ok(product);
        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Converts full Product entity to slim DTO for list view
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
     * Slim DTO for product list view (performance optimized)
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
