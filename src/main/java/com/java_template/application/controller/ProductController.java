package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ui/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            logger.info("Getting products with filters - search: {}, category: {}, minPrice: {}, maxPrice: {}, page: {}, pageSize: {}", 
                       search, category, minPrice, maxPrice, page, pageSize);

            // Build search condition
            SearchConditionRequest condition = buildSearchCondition(search, category, minPrice, maxPrice);
            
            // Get products from entity service
            List<EntityResponse<Product>> productResponses;
            if (condition != null) {
                productResponses = entityService.getItemsByCondition(
                    Product.class, 
                    Product.ENTITY_NAME, 
                    Product.ENTITY_VERSION, 
                    condition, 
                    true
                );
            } else {
                productResponses = entityService.getItems(
                    Product.class, 
                    Product.ENTITY_NAME, 
                    Product.ENTITY_VERSION, 
                    null, 
                    null, 
                    null
                );
            }

            // Convert to slim DTOs
            List<Map<String, Object>> slimProducts = productResponses.stream()
                .map(response -> convertToSlimDto(response.getData()))
                .collect(Collectors.toList());

            // Apply pagination
            int totalElements = slimProducts.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);
            
            List<Map<String, Object>> paginatedProducts = slimProducts.subList(startIndex, endIndex);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("products", paginatedProducts);
            
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("pageSize", pageSize);
            pagination.put("totalElements", totalElements);
            pagination.put("totalPages", totalPages);
            response.put("pagination", pagination);

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting products", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        try {
            logger.info("Getting product by SKU: {}", sku);

            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
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
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private SearchConditionRequest buildSearchCondition(String search, String category, Double minPrice, Double maxPrice) {
        List<Condition> conditions = new ArrayList<>();

        // Free-text search on name or description
        if (search != null && !search.trim().isEmpty()) {
            // For simplicity, just search on name for now
            conditions.add(Condition.of("$.name", "CONTAINS", search));
        }

        // Category filter
        if (category != null && !category.trim().isEmpty()) {
            conditions.add(Condition.of("$.category", "EQUALS", category));
        }

        // Price range filters
        if (minPrice != null) {
            conditions.add(Condition.of("$.price", "GTE", minPrice));
        }
        if (maxPrice != null) {
            conditions.add(Condition.of("$.price", "LTE", maxPrice));
        }

        if (conditions.isEmpty()) {
            return null;
        }

        SearchConditionRequest condition = new SearchConditionRequest();
        if (conditions.size() == 1) {
            condition.setType("simple");
            condition.setConditions(conditions);
        } else {
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(conditions);
        }

        return condition;
    }

    private Map<String, Object> convertToSlimDto(Product product) {
        Map<String, Object> slimDto = new HashMap<>();
        slimDto.put("sku", product.getSku());
        slimDto.put("name", product.getName());
        slimDto.put("description", product.getDescription());
        slimDto.put("price", product.getPrice());
        slimDto.put("quantityAvailable", product.getQuantityAvailable());
        slimDto.put("category", product.getCategory());
        
        // Extract image URL from media if available
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            Optional<String> imageUrl = product.getMedia().stream()
                .filter(media -> "image".equals(media.getType()))
                .map(Product.Media::getUrl)
                .findFirst();
            slimDto.put("imageUrl", imageUrl.orElse(null));
        } else {
            slimDto.put("imageUrl", null);
        }
        
        return slimDto;
    }
}
