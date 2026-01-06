package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ProductController - OMS Product Catalog API
 * Endpoints: GET /ui/products, GET /ui/products/{sku}
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
    public ResponseEntity<PageResult<ProductDTO>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
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
                
                GroupCondition searchGroup = new GroupCondition()
                        .withOperator(GroupCondition.Operator.OR)
                        .withConditions(List.of(nameCondition, descCondition));
                conditions.add(searchGroup);
            }

            // Category filter
            if (category != null && !category.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.category")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(category)));
            }

            // Price range filter
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

            PageResult<EntityWithMetadata<Product>> result;
            if (conditions.isEmpty()) {
                result = entityService.findAll(modelSpec, Product.class,
                        SearchAndRetrievalParams.builder()
                                .pageSize(pageSize)
                                .pageNumber(page)
                                .build());
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);

                result = entityService.search(modelSpec, condition, Product.class,
                        SearchAndRetrievalParams.builder()
                                .pageSize(pageSize)
                                .pageNumber(page)
                                .build());
            }

            // Map to slim DTO
            List<ProductDTO> dtos = result.data().stream()
                    .map(this::toProductDTO)
                    .toList();

            PageResult<ProductDTO> dtoResult = PageResult.of(
                    result.searchId(),
                    dtos,
                    result.pageNumber(),
                    result.pageSize(),
                    result.totalElements()
            );

            logger.info("Found {} products", result.totalElements());
            return ResponseEntity.ok(dtoResult);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to search products: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get product by SKU (full document)
     * GET /ui/products/{sku}
     */
    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            
            SimpleCondition skuCondition = new SimpleCondition()
                    .withJsonPath("$.sku")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(sku));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(skuCondition));

            List<EntityWithMetadata<Product>> results = entityService.search(
                    modelSpec, condition, Product.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Retrieved product with SKU: {}", sku);
            return ResponseEntity.ok(results.get(0).entity());
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve product with SKU '%s': %s", sku, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Convert Product to slim DTO for list view
     */
    private ProductDTO toProductDTO(EntityWithMetadata<Product> productWithMetadata) {
        Product product = productWithMetadata.entity();
        ProductDTO dto = new ProductDTO();
        dto.setSku(product.getSku());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setQuantityAvailable(product.getQuantityAvailable());
        dto.setCategory(product.getCategory());
        
        // Extract image URL from media if available
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            for (Product.Media media : product.getMedia()) {
                if ("image".equals(media.getType())) {
                    dto.setImageUrl(media.getUrl());
                    break;
                }
            }
        }
        
        return dto;
    }

    /**
     * Slim Product DTO for list view
     */
    public static class ProductDTO {
        private String sku;
        private String name;
        private String description;
        private Double price;
        private Integer quantityAvailable;
        private String category;
        private String imageUrl;

        // Getters and setters
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Integer getQuantityAvailable() { return quantityAvailable; }
        public void setQuantityAvailable(Integer quantityAvailable) { this.quantityAvailable = quantityAvailable; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}

