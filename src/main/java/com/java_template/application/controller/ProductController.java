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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ProductController - REST endpoints for product catalog operations
 * 
 * Provides UI-facing APIs for product search, filtering, and retrieval.
 * Base path: /ui/products
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
     * GET /ui/products - List products with filters and pagination
     * Returns slim DTOs for performance
     */
    @GetMapping
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            logger.info("Searching products with filters - search: {}, category: {}, minPrice: {}, maxPrice: {}", 
                       search, category, minPrice, maxPrice);

            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            
            // Build search conditions
            List<QueryCondition> conditions = new ArrayList<>();
            
            // Free-text search on name or description
            if (search != null && !search.trim().isEmpty()) {
                SimpleCondition nameCondition = new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                
                SimpleCondition descCondition = new SimpleCondition()
                        .withJsonPath("$.description")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                
                // Create OR condition for name or description
                GroupCondition searchCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.OR)
                        .withConditions(List.of(nameCondition, descCondition));

                conditions.add(searchCondition);
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
                products = entityService.findAll(modelSpec, Product.class);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                products = entityService.search(modelSpec, condition, Product.class);
            }

            // Convert to slim DTOs
            List<ProductSlimDto> slimProducts = products.stream()
                    .map(this::toSlimDto)
                    .collect(Collectors.toList());

            // Apply pagination (simple in-memory pagination for demo)
            int start = page * pageSize;
            int end = Math.min(start + pageSize, slimProducts.size());
            List<ProductSlimDto> pageContent = start < slimProducts.size() ? 
                    slimProducts.subList(start, end) : new ArrayList<>();

            ProductListResponse response = new ProductListResponse();
            response.setContent(pageContent);
            response.setPage(page);
            response.setPageSize(pageSize);
            response.setTotalElements(slimProducts.size());
            response.setTotalPages((int) Math.ceil((double) slimProducts.size() / pageSize));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /ui/products/{sku} - Get product details
     * Returns full product document
     */
    @GetMapping("/{sku}")
    public ResponseEntity<EntityWithMetadata<Product>> getProductBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> product = entityService.findByBusinessId(modelSpec, sku, "sku", Product.class);
            
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
     * Convert Product entity to slim DTO for list view
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

    // DTOs for responses

    @Data
    public static class ProductSlimDto {
        private String sku;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer quantityAvailable;
        private String category;
        private String imageUrl;
    }

    @Data
    public static class ProductListResponse {
        private List<ProductSlimDto> content;
        private Integer page;
        private Integer pageSize;
        private Integer totalElements;
        private Integer totalPages;
    }
}
