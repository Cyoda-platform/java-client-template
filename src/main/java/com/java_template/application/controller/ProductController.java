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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ABOUTME: Product catalog controller providing search, filtering, and detail views
 * for the OMS product catalog with slim DTOs for list views and full documents for detail views.
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
     * GET /ui/products?search=&category=&minPrice=&maxPrice=&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<ProductSlimDto>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            Pageable pageable) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

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
                GroupCondition searchCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.OR)
                        .withConditions(List.of(nameCondition, descCondition));

                conditions.add(searchCondition);
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
                // No filters - use paginated findAll
                Page<EntityWithMetadata<Product>> productPage = entityService.findAll(modelSpec, pageable, Product.class);
                products = productPage.getContent();
                
                // Convert to slim DTOs
                List<ProductSlimDto> slimProducts = products.stream()
                        .map(this::toSlimDto)
                        .toList();
                
                return ResponseEntity.ok(new PageImpl<>(slimProducts, pageable, productPage.getTotalElements()));
            } else {
                // With filters - get all matching and manually paginate
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);

                products = entityService.search(modelSpec, groupCondition, Product.class);

                // Convert to slim DTOs
                List<ProductSlimDto> slimProducts = products.stream()
                        .map(this::toSlimDto)
                        .toList();

                // Manual pagination
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), slimProducts.size());
                List<ProductSlimDto> pageContent = start < slimProducts.size()
                        ? slimProducts.subList(start, end)
                        : new ArrayList<>();

                return ResponseEntity.ok(new PageImpl<>(pageContent, pageable, slimProducts.size()));
            }

        } catch (Exception e) {
            logger.error("Failed to search products", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to search products: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get full product document by SKU
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
            logger.error("Failed to get product by SKU: {}", sku, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve product with SKU '%s': %s", sku, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
