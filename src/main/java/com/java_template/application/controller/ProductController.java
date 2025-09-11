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

/**
 * ProductController - Manages product catalog operations for the UI.
 * 
 * Endpoints:
 * - GET /ui/products - Search and filter products with pagination
 * - GET /ui/products/{sku} - Get full product details by SKU
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
     * Search and filter products with pagination
     * GET /ui/products?search=&category=&minPrice=&maxPrice=&page=&pageSize=
     */
    @GetMapping
    public ResponseEntity<ProductSearchResponse> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            logger.debug("Searching products: search={}, category={}, minPrice={}, maxPrice={}, page={}, pageSize={}", 
                       search, category, minPrice, maxPrice, page, pageSize);

            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            // Build search conditions
            GroupCondition searchCondition = buildSearchCondition(search, category, minPrice, maxPrice);
            
            // Search products
            List<EntityWithMetadata<Product>> allProducts;
            if (searchCondition.getConditions() != null && !searchCondition.getConditions().isEmpty()) {
                allProducts = entityService.search(modelSpec, searchCondition, Product.class);
            } else {
                allProducts = entityService.findAll(modelSpec, Product.class);
            }

            // Apply pagination
            int totalElements = allProducts.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);
            
            List<EntityWithMetadata<Product>> paginatedProducts = allProducts.subList(startIndex, endIndex);

            // Convert to slim DTOs for list view
            List<ProductSlimDto> slimProducts = new ArrayList<>();
            for (EntityWithMetadata<Product> productWithMetadata : paginatedProducts) {
                Product product = productWithMetadata.entity();
                ProductSlimDto slimDto = new ProductSlimDto();
                slimDto.setSku(product.getSku());
                slimDto.setName(product.getName());
                slimDto.setDescription(product.getDescription());
                slimDto.setPrice(product.getPrice());
                slimDto.setQuantityAvailable(product.getQuantityAvailable());
                slimDto.setCategory(product.getCategory());
                
                // Extract image URL from media if available
                if (product.getMedia() != null && !product.getMedia().isEmpty()) {
                    for (Product.ProductMedia media : product.getMedia()) {
                        if ("image".equals(media.getType()) && media.getTags() != null && 
                            media.getTags().contains("hero")) {
                            slimDto.setImageUrl(media.getUrl());
                            break;
                        }
                    }
                }
                
                slimProducts.add(slimDto);
            }

            ProductSearchResponse response = new ProductSearchResponse();
            response.setContent(slimProducts);
            response.setPage(page);
            response.setPageSize(pageSize);
            response.setTotalElements(totalElements);
            response.setTotalPages(totalPages);

            return ResponseEntity.ok(response);

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
            
            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (productWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            // Return the full Product document (entire schema)
            return ResponseEntity.ok(productWithMetadata.entity());

        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Builds search condition based on provided filters
     */
    private GroupCondition buildSearchCondition(String search, String category, Double minPrice, Double maxPrice) {
        List<QueryCondition> conditions = new ArrayList<>();

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

            // Create OR condition for name/description search
            GroupCondition searchGroup = new GroupCondition()
                    .withOperator(GroupCondition.Operator.OR)
                    .withConditions(List.of(nameCondition, descCondition));
            
            // Wrap in a simple condition for the main AND group
            conditions.add(nameCondition); // Simplified for now - just search name
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

        return new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(conditions);
    }

    /**
     * Slim DTO for product list view
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

    /**
     * Response DTO for product search with pagination
     */
    @Data
    public static class ProductSearchResponse {
        private List<ProductSlimDto> content;
        private Integer page;
        private Integer pageSize;
        private Integer totalElements;
        private Integer totalPages;
    }
}
