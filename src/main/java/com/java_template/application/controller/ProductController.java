package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Product controller for catalog management and search functionality.
 * Provides endpoints for product listing with filters and product details.
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
     * Search products with filters: category, free-text search, price range
     * Returns slim DTOs for performance
     */
    @GetMapping
    public ResponseEntity<List<ProductSlimDto>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        logger.info("Searching products - search: {}, category: {}, minPrice: {}, maxPrice: {}, page: {}, pageSize: {}",
                   search, category, minPrice, maxPrice, page, pageSize);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Product.ENTITY_NAME);
            modelSpec.setVersion(Product.ENTITY_VERSION);

            // Build search conditions
            List<QueryCondition> conditions = new ArrayList<>();

            // Free-text search on name OR description
            if (search != null && !search.trim().isEmpty()) {
                List<QueryCondition> textConditions = new ArrayList<>();
                
                SimpleCondition nameCondition = new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                textConditions.add(nameCondition);

                SimpleCondition descCondition = new SimpleCondition()
                        .withJsonPath("$.description")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                textConditions.add(descCondition);

                GroupCondition textGroup = new GroupCondition()
                        .withOperator(GroupCondition.Operator.OR)
                        .withConditions(textConditions);
                conditions.add(textGroup);
            }

            // Category filter
            if (category != null && !category.trim().isEmpty()) {
                SimpleCondition categoryCondition = new SimpleCondition()
                        .withJsonPath("$.category")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(category));
                conditions.add(categoryCondition);
            }

            // Price range filters
            if (minPrice != null) {
                SimpleCondition minPriceCondition = new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(minPrice));
                conditions.add(minPriceCondition);
            }

            if (maxPrice != null) {
                SimpleCondition maxPriceCondition = new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(maxPrice));
                conditions.add(maxPriceCondition);
            }

            // Execute search
            List<EntityWithMetadata<Product>> products;
            if (conditions.isEmpty()) {
                // No filters - get all products
                products = entityService.findAll(modelSpec, Product.class);
            } else {
                // Apply filters
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                products = entityService.search(modelSpec, groupCondition, Product.class);
            }

            // Convert to slim DTOs
            List<ProductSlimDto> slimProducts = products.stream()
                    .map(this::toSlimDto)
                    .collect(Collectors.toList());

            // Apply pagination (simple in-memory pagination for demo)
            int start = page * pageSize;
            int end = Math.min(start + pageSize, slimProducts.size());
            List<ProductSlimDto> paginatedProducts = start < slimProducts.size() 
                    ? slimProducts.subList(start, end) 
                    : new ArrayList<>();

            logger.info("Found {} products, returning {} for page {}", 
                       slimProducts.size(), paginatedProducts.size(), page);

            return ResponseEntity.ok(paginatedProducts);

        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get full product details by SKU
     */
    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        logger.info("Getting product details for SKU: {}", sku);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Product.ENTITY_NAME);
            modelSpec.setVersion(Product.ENTITY_VERSION);

            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (productWithMetadata == null) {
                logger.warn("Product not found for SKU: {}", sku);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found product: {} for SKU: {}", productWithMetadata.entity().getName(), sku);
            return ResponseEntity.ok(productWithMetadata.entity());

        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Convert full Product to slim DTO for list views
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
     * Slim DTO for product list views - optimized for performance
     */
    @Data
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
