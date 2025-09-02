package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/ui/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    
    @Autowired
    private EntityService entityService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        logger.info("Searching products with filters - search: {}, category: {}, minPrice: {}, maxPrice: {}, page: {}, pageSize: {}", 
                   search, category, minPrice, maxPrice, page, pageSize);

        try {
            // Build search conditions
            List<Condition> conditions = new ArrayList<>();
            
            // Free text search on name or description
            if (search != null && !search.trim().isEmpty()) {
                Condition nameCondition = Condition.of("$.name", "CONTAINS", search);
                Condition descCondition = Condition.of("$.description", "CONTAINS", search);
                
                SearchConditionRequest orCondition = new SearchConditionRequest();
                orCondition.setType("group");
                orCondition.setOperator("OR");
                orCondition.setConditions(List.of(nameCondition, descCondition));
                
                conditions.add(Condition.of("$", "CUSTOM", orCondition));
            }
            
            // Category filter
            if (category != null && !category.trim().isEmpty()) {
                conditions.add(Condition.of("$.category", "EQUALS", category));
            }
            
            // Price range filters
            if (minPrice != null) {
                conditions.add(Condition.of("$.price", "GREATER_THAN_OR_EQUAL", minPrice.toString()));
            }
            
            if (maxPrice != null) {
                conditions.add(Condition.of("$.price", "LESS_THAN_OR_EQUAL", maxPrice.toString()));
            }

            // Get products based on conditions
            List<EntityResponse<Product>> productResponses;
            
            if (conditions.isEmpty()) {
                // No filters - get all products
                productResponses = entityService.findAll(Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION);
            } else {
                // Apply filters
                SearchConditionRequest searchCondition = new SearchConditionRequest();
                searchCondition.setType("group");
                searchCondition.setOperator("AND");
                searchCondition.setConditions(conditions);
                
                productResponses = entityService.findByCondition(
                    Product.class, 
                    Product.ENTITY_NAME, 
                    Product.ENTITY_VERSION, 
                    searchCondition, 
                    true
                );
            }

            // Convert to slim DTOs for list view
            List<Map<String, Object>> slimProducts = productResponses.stream()
                .map(this::convertToSlimDto)
                .collect(Collectors.toList());

            // Apply pagination
            int totalElements = slimProducts.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);
            
            List<Map<String, Object>> paginatedProducts = slimProducts.subList(startIndex, endIndex);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("content", paginatedProducts);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);
            response.put("hasNext", page < totalPages - 1);
            response.put("hasPrevious", page > 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to search products: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        logger.info("Getting product details for SKU: {}", sku);

        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(skuCondition));

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class,
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                condition,
                true
            );

            if (productResponse.isPresent()) {
                return ResponseEntity.ok(productResponse.get().getData());
            } else {
                logger.warn("Product not found for SKU: {}", sku);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Failed to get product by SKU {}: {}", sku, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> convertToSlimDto(EntityResponse<Product> productResponse) {
        Product product = productResponse.getData();
        Map<String, Object> slimDto = new HashMap<>();
        
        slimDto.put("sku", product.getSku());
        slimDto.put("name", product.getName());
        slimDto.put("description", product.getDescription());
        slimDto.put("price", product.getPrice());
        slimDto.put("quantityAvailable", product.getQuantityAvailable());
        slimDto.put("category", product.getCategory());
        
        // Extract image URL from media if available
        String imageUrl = null;
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            imageUrl = product.getMedia().stream()
                .filter(media -> "image".equals(media.getType()))
                .map(Product.Media::getUrl)
                .findFirst()
                .orElse(null);
        }
        slimDto.put("imageUrl", imageUrl);
        
        return slimDto;
    }
}
