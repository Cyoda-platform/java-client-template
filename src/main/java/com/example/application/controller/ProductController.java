package com.example.application.controller;

import com.example.application.entity.product.version_1.Product;
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
import java.util.stream.Collectors;

/**
 * ProductController - Product catalog endpoints
 * GET /ui/products - List with filters (category, search, price range)
 * GET /ui/products/{sku} - Full product detail
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
     * List products with filters
     * GET /ui/products?search=&category=&minPrice=&maxPrice=&page=&pageSize=
     */
    @GetMapping
    public ResponseEntity<PageResult<ProductSlimDTO>> listProducts(
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
                List<QueryCondition> searchConditions = new ArrayList<>();
                searchConditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search)));
                searchConditions.add(new SimpleCondition()
                        .withJsonPath("$.description")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search)));
                
                GroupCondition searchGroup = new GroupCondition()
                        .withOperator(GroupCondition.Operator.OR)
                        .withConditions(searchConditions);
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
            List<ProductSlimDTO> slimDTOs = result.data().stream()
                    .map(this::toSlimDTO)
                    .collect(Collectors.toList());

            PageResult<ProductSlimDTO> slimResult = new PageResult<>(
                    slimDTOs,
                    result.pageNumber(),
                    result.pageSize(),
                    result.totalElements(),
                    result.totalPages(),
                    result.searchId()
            );

            logger.info("Listed {} products (page {}/{})", slimDTOs.size(), page, result.totalPages());
            return ResponseEntity.ok(slimResult);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to list products: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get full product detail by SKU
     * GET /ui/products/{sku}
     */
    @GetMapping("/{sku}")
    public ResponseEntity<EntityWithMetadata<Product>> getProductBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> response = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Retrieved product: {}", sku);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve product '%s': %s", sku, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Slim DTO for product list view
     */
    public static class ProductSlimDTO {
        public String sku;
        public String name;
        public String description;
        public Double price;
        public Integer quantityAvailable;
        public String category;
        public String imageUrl;

        public ProductSlimDTO(String sku, String name, String description, Double price,
                             Integer quantityAvailable, String category, String imageUrl) {
            this.sku = sku;
            this.name = name;
            this.description = description;
            this.price = price;
            this.quantityAvailable = quantityAvailable;
            this.category = category;
            this.imageUrl = imageUrl;
        }
    }

    private ProductSlimDTO toSlimDTO(EntityWithMetadata<Product> productWithMetadata) {
        Product product = productWithMetadata.entity();
        String imageUrl = null;
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            imageUrl = product.getMedia().stream()
                    .filter(m -> "image".equals(m.getType()))
                    .findFirst()
                    .map(Product.Media::getUrl)
                    .orElse(null);
        }
        return new ProductSlimDTO(
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantityAvailable(),
                product.getCategory(),
                imageUrl
        );
    }
}

