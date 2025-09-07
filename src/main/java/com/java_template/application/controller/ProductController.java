package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Product Controller - Manages product catalog operations with search and filtering
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

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);

            // Build search condition
            List<Condition> conditions = new ArrayList<>();

            // Free-text search on name OR description
            if (search != null && !search.trim().isEmpty()) {
                SearchConditionRequest textCondition = SearchConditionRequest.group("OR",
                    Condition.of("$.name", "CONTAINS", search),
                    Condition.of("$.description", "CONTAINS", search));
                conditions.add(new Condition("group", null, null, "OR", textCondition));
            }

            // Category filter
            if (category != null && !category.trim().isEmpty()) {
                conditions.add(Condition.of("$.category", "EQUALS", category));
            }

            // Price range filters
            if (minPrice != null) {
                conditions.add(Condition.of("$.price", "GREATER_THAN_OR_EQUAL", minPrice));
            }
            if (maxPrice != null) {
                conditions.add(Condition.of("$.price", "LESS_THAN_OR_EQUAL", maxPrice));
            }

            // Search products
            List<EntityWithMetadata<Product>> products;
            if (conditions.isEmpty()) {
                // No filters, get all products
                products = entityService.findAll(modelSpec, Product.class);
            } else {
                SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                products = entityService.search(modelSpec, searchCondition, Product.class);
            }
            
            // Apply pagination manually (since EntityService doesn't support pagination directly)
            int totalElements = products.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);
            
            List<ProductSlimDto> slimProducts = products.subList(startIndex, endIndex)
                .stream()
                .map(this::mapToSlimDto)
                .collect(Collectors.toList());
            
            ProductSearchResponse response = new ProductSearchResponse();
            response.setContent(slimProducts);
            response.setPage(page);
            response.setSize(pageSize);
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
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Map full Product entity to slim DTO for list view
     */
    private ProductSlimDto mapToSlimDto(EntityWithMetadata<Product> productWithMetadata) {
        Product product = productWithMetadata.entity();
        ProductSlimDto dto = new ProductSlimDto();
        dto.setSku(product.getSku());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setQuantityAvailable(product.getQuantityAvailable());
        dto.setCategory(product.getCategory());

        // Extract image URL from media array
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            dto.setImageUrl(product.getMedia().stream()
                .filter(media -> "image".equals(media.getType()))
                .findFirst()
                .map(Product.ProductMedia::getUrl)
                .orElse(null));
        }

        return dto;
    }

    /**
     * Slim DTO for product list view
     */
    public static class ProductSlimDto {
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

    /**
     * Response wrapper for product search with pagination
     */
    public static class ProductSearchResponse {
        private List<ProductSlimDto> content;
        private int page;
        private int size;
        private int totalElements;
        private int totalPages;

        // Getters and setters
        public List<ProductSlimDto> getContent() { return content; }
        public void setContent(List<ProductSlimDto> content) { this.content = content; }
        
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        
        public int getTotalElements() { return totalElements; }
        public void setTotalElements(int totalElements) { this.totalElements = totalElements; }
        
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }
}
