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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ABOUTME: REST controller for Product catalog endpoints with search and filter capabilities.
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
     * List products with search, category, and price range filters
     * GET /ui/products?search=&category=&minPrice=&maxPrice=&page=&pageSize=
     */
    @GetMapping
    public ResponseEntity<Page<ProductSlimDTO>> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            Pageable pageable) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            
            List<QueryCondition> conditions = new ArrayList<>();

            // Add search condition (free-text on name or description)
            if (search != null && !search.trim().isEmpty()) {
                SimpleCondition nameCondition = new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                conditions.add(nameCondition);

                SimpleCondition descCondition = new SimpleCondition()
                        .withJsonPath("$.description")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(search));
                conditions.add(descCondition);
            }

            // Add category filter
            if (category != null && !category.trim().isEmpty()) {
                SimpleCondition categoryCondition = new SimpleCondition()
                        .withJsonPath("$.category")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(category));
                conditions.add(categoryCondition);
            }

            // Add price range filters
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
                products = entityService.findAll(modelSpec, Product.class);
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                products = entityService.search(modelSpec, groupCondition, Product.class);
            }

            // Convert to slim DTOs
            List<ProductSlimDTO> slimDTOs = products.stream()
                    .map(p -> toSlimDTO(p.entity()))
                    .collect(Collectors.toList());

            // Manually paginate
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), slimDTOs.size());
            List<ProductSlimDTO> pageContent = start < slimDTOs.size()
                    ? slimDTOs.subList(start, end)
                    : new ArrayList<>();

            Page<ProductSlimDTO> page = new PageImpl<>(pageContent, pageable, slimDTOs.size());
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            logger.error("Failed to list products: {}", e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to list products: %s", e.getMessage())
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
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> response = entityService.findByBusinessId(
                    modelSpec, sku, "sku", Product.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to get product by SKU {}: {}", sku, e.getMessage());
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve product with SKU '%s': %s", sku, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Convert Product to slim DTO for list views
     */
    private ProductSlimDTO toSlimDTO(Product product) {
        ProductSlimDTO dto = new ProductSlimDTO();
        dto.setSku(product.getSku());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setQuantityAvailable(product.getQuantityAvailable());
        dto.setCategory(product.getCategory());
        
        // Extract image URL from media if available
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            for (Product.Media media : product.getMedia()) {
                if ("image".equals(media.getType()) && media.getUrl() != null) {
                    dto.setImageUrl(media.getUrl());
                    break;
                }
            }
        }
        
        return dto;
    }

    /**
     * Slim DTO for product list responses
     */
    @Getter
    @Setter
    public static class ProductSlimDTO {
        private String sku;
        private String name;
        private String description;
        private Double price;
        private Integer quantityAvailable;
        private String category;
        private String imageUrl;
    }
}

