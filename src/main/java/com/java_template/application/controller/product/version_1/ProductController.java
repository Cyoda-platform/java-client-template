package com.java_template.application.controller.product.version_1;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * ProductController handles REST API endpoints for product operations.
 * This controller is a proxy to the EntityService for Product entities.
 */
@RestController
@RequestMapping("/ui/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private EntityService entityService;

    /**
     * Search and filter products with pagination.
     * 
     * @param search Free-text search on name/description
     * @param category Filter by category
     * @param minPrice Minimum price filter
     * @param maxPrice Maximum price filter
     * @param page Page number (default: 0)
     * @param pageSize Page size (default: 20)
     * @return List of slim product DTOs with pagination info
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer pageSize) {
        
        logger.info("Searching products with search={}, category={}, minPrice={}, maxPrice={}, page={}, pageSize={}", 
                   search, category, minPrice, maxPrice, page, pageSize);

        try {
            // Build search condition
            SearchConditionRequest condition = buildSearchCondition(search, category, minPrice, maxPrice);
            
            // Get products from entity service
            List<EntityResponse<Product>> productResponses = entityService.getItemsByCondition(
                Product.class, condition, false);
            
            // Apply pagination manually (simplified for demo)
            int start = page * pageSize;
            int end = Math.min(start + pageSize, productResponses.size());
            List<EntityResponse<Product>> paginatedResponses = productResponses.subList(
                Math.min(start, productResponses.size()), 
                Math.min(end, productResponses.size()));
            
            // Convert to slim DTOs
            List<Map<String, Object>> slimProducts = paginatedResponses.stream()
                .map(this::toSlimProductDto)
                .collect(Collectors.toList());
            
            // Build response with pagination info
            Map<String, Object> response = new HashMap<>();
            response.put("content", slimProducts);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalElements", productResponses.size());
            response.put("totalPages", (int) Math.ceil((double) productResponses.size() / pageSize));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get full product details by SKU.
     * 
     * @param sku Product SKU
     * @return Full Product document with complete schema
     */
    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        logger.info("Getting product by SKU: {}", sku);

        try {
            // Search by SKU field
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("sku", "equals", sku));

            var productResponse = entityService.getFirstItemByCondition(Product.class, condition, false);
            
            if (productResponse.isPresent()) {
                return ResponseEntity.ok(productResponse.get().getData());
            } else {
                logger.warn("Product not found with SKU: {}", sku);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Build search condition based on query parameters.
     */
    private SearchConditionRequest buildSearchCondition(String search, String category,
                                                       BigDecimal minPrice, BigDecimal maxPrice) {
        List<Condition> conditions = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            // Simple text search on name and description
            conditions.add(Condition.of("name", "contains", search));
            conditions.add(Condition.of("description", "contains", search));
        }

        if (category != null && !category.trim().isEmpty()) {
            conditions.add(Condition.of("category", "equals", category));
        }

        if (minPrice != null) {
            conditions.add(Condition.of("price", "gte", minPrice));
        }

        if (maxPrice != null) {
            conditions.add(Condition.of("price", "lte", maxPrice));
        }

        if (conditions.isEmpty()) {
            // Return empty condition if no filters
            return SearchConditionRequest.group("and");
        }

        return SearchConditionRequest.group("and", conditions.toArray(new Condition[0]));
    }

    /**
     * Convert Product entity to slim DTO for list responses.
     */
    private Map<String, Object> toSlimProductDto(EntityResponse<Product> productResponse) {
        Product product = productResponse.getData();
        Map<String, Object> dto = new HashMap<>();
        
        dto.put("sku", product.getSku());
        dto.put("name", product.getName());
        dto.put("description", product.getDescription());
        dto.put("price", product.getPrice());
        dto.put("quantityAvailable", product.getQuantityAvailable());
        dto.put("category", product.getCategory());
        
        // Add image URL from media if available
        if (product.getMedia() != null && product.getMedia().containsKey("images")) {
            Object images = product.getMedia().get("images");
            if (images instanceof List && !((List<?>) images).isEmpty()) {
                dto.put("imageUrl", ((List<?>) images).get(0));
            }
        }
        
        return dto;
    }
}
