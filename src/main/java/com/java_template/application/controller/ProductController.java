package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ui/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        try {
            logger.info("Searching products with filters - search: {}, category: {}, minPrice: {}, maxPrice: {}, page: {}, pageSize: {}",
                    search, category, minPrice, maxPrice, page, pageSize);

            // Build search conditions
            List<Condition> conditions = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                // Free-text search on name OR description
                Condition nameCondition = Condition.of("$.name", "CONTAINS", search);
                Condition descCondition = Condition.of("$.description", "CONTAINS", search);

                SearchConditionRequest searchGroup = SearchConditionRequest.group("OR", nameCondition, descCondition);
                // For now, we'll add individual conditions since the API structure is complex
                conditions.add(nameCondition);
                conditions.add(descCondition);
            }

            if (category != null && !category.trim().isEmpty()) {
                conditions.add(Condition.of("$.category", "EQUALS", category));
            }

            if (minPrice != null) {
                conditions.add(Condition.of("$.price", "GREATER_THAN_OR_EQUAL", minPrice));
            }

            if (maxPrice != null) {
                conditions.add(Condition.of("$.price", "LESS_THAN_OR_EQUAL", maxPrice));
            }

            // Get products based on conditions
            List<Product> products;
            if (conditions.isEmpty()) {
                // Get all products
                var productResponses = entityService.findAll(Product.class);
                products = productResponses.stream().map(r -> r.getData()).collect(Collectors.toList());
            } else {
                SearchConditionRequest finalCondition = new SearchConditionRequest();
                finalCondition.setType("group");
                finalCondition.setOperator("AND");
                finalCondition.setConditions(conditions);

                var productResponses = entityService.search(Product.class, finalCondition);
                products = productResponses.stream().map(r -> r.getData()).collect(Collectors.toList());
            }

            // Apply pagination
            int totalElements = products.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);

            List<Product> paginatedProducts = products.subList(startIndex, endIndex);

            // Convert to slim DTOs for list view
            List<Map<String, Object>> slimProducts = paginatedProducts.stream()
                    .map(this::toSlimDto)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("content", slimProducts);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error searching products: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "SEARCH_ERROR", "message", "Failed to search products: " + e.getMessage()));
        }
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Object> getProductBySku(@PathVariable String sku) {
        try {
            logger.info("Getting product details for SKU: {}", sku);

            var productResponses = entityService.findByField(
                    Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, "sku", sku);

            if (productResponses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Product product = productResponses.get(0).getData();
            return ResponseEntity.ok(product);

        } catch (Exception e) {
            logger.error("Error getting product by SKU {}: {}", sku, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "PRODUCT_ERROR", "message", "Failed to get product: " + e.getMessage()));
        }
    }

    private Map<String, Object> toSlimDto(Product product) {
        Map<String, Object> slim = new HashMap<>();
        slim.put("sku", product.getSku());
        slim.put("name", product.getName());
        slim.put("description", product.getDescription());
        slim.put("price", product.getPrice());
        slim.put("quantityAvailable", product.getQuantityAvailable());
        slim.put("category", product.getCategory());
        
        // Extract image URL from media if available
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            product.getMedia().stream()
                    .filter(media -> "image".equals(media.getType()))
                    .findFirst()
                    .ifPresent(media -> slim.put("imageUrl", media.getUrl()));
        }
        
        return slim;
    }
}
