package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Product controller providing REST endpoints for catalog management
 * including product search, filtering, and CRUD operations.
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
    public ResponseEntity<Page<ProductSlimDto>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            Pageable pageable) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            
            List<QueryCondition> conditions = new ArrayList<>();

            // Free-text search on name OR description
            if (search != null && !search.trim().isEmpty()) {
                List<QueryCondition> searchConditions = new ArrayList<>();
                
                SimpleCondition nameCondition = new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                searchConditions.add(nameCondition);
                
                SimpleCondition descCondition = new SimpleCondition()
                        .withJsonPath("$.description")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                searchConditions.add(descCondition);
                
                GroupCondition searchGroup = new GroupCondition()
                        .withOperator(GroupCondition.Operator.OR)
                        .withConditions(searchConditions);
                conditions.add(searchGroup);
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

            List<EntityWithMetadata<Product>> products;
            if (conditions.isEmpty()) {
                // Use paginated findAll when no filters
                Page<EntityWithMetadata<Product>> productPage = entityService.findAll(modelSpec, pageable, Product.class);
                products = productPage.getContent();
                
                // Convert to slim DTOs
                List<ProductSlimDto> slimProducts = products.stream()
                        .map(this::toSlimDto)
                        .toList();
                
                Page<ProductSlimDto> slimPage = new PageImpl<>(slimProducts, pageable, productPage.getTotalElements());
                return ResponseEntity.ok(slimPage);
            } else {
                // For filtered results, get all matching results then manually paginate
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                products = entityService.search(modelSpec, groupCondition, Product.class);
                
                // Convert to slim DTOs
                List<ProductSlimDto> slimProducts = products.stream()
                        .map(this::toSlimDto)
                        .toList();
                
                // Manually paginate the filtered results
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), slimProducts.size());
                List<ProductSlimDto> pageContent = start < slimProducts.size()
                    ? slimProducts.subList(start, end)
                    : new ArrayList<>();

                Page<ProductSlimDto> page = new PageImpl<>(pageContent, pageable, slimProducts.size());
                return ResponseEntity.ok(page);
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
     * Get product by SKU (full document)
     * GET /ui/products/{sku}
     */
    @GetMapping("/{sku}")
    public ResponseEntity<EntityWithMetadata<Product>> getProductBySku(@PathVariable String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> product = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (product == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(product);
        } catch (Exception e) {
            logger.error("Failed to retrieve product with SKU: {}", sku, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve product with SKU '%s': %s", sku, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Create a new product
     * POST /ui/products
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Product>> createProduct(@Valid @RequestBody Product product) {
        try {
            // Check for duplicate SKU
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, product.getSku(), "sku", Product.class);

            if (existing != null) {
                logger.warn("Product with SKU {} already exists", product.getSku());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Product already exists with SKU: %s", product.getSku())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Product> response = entityService.create(product);
            logger.info("Product created with SKU: {}", product.getSku());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{sku}")
                .buildAndExpand(product.getSku())
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
     * Update product by technical UUID
     * PUT /ui/products/id/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Product>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody Product product,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Product> response = entityService.update(id, product, transition);
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
     * Update product inventory (triggers processor)
     * PUT /ui/products/{sku}/inventory
     */
    @PutMapping("/{sku}/inventory")
    public ResponseEntity<EntityWithMetadata<Product>> updateProductInventory(
            @PathVariable String sku,
            @Valid @RequestBody Product product) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> existing = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (existing == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Product> response = entityService.update(
                    existing.metadata().getId(), product, "update_inventory");
            logger.info("Product inventory updated for SKU: {}", sku);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update product inventory for SKU: {}", sku, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update product inventory for SKU '%s': %s", sku, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
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
     * Slim DTO for product list views
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
