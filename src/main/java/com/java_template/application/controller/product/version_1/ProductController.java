package com.java_template.application.controller.product.version_1;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProductController handles product catalog operations for the UI.
 */
@RestController
@RequestMapping("/ui/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Search and filter products with pagination
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        logger.info("Searching products: search={}, category={}, minPrice={}, maxPrice={}, page={}, pageSize={}", 
            search, category, minPrice, maxPrice, page, pageSize);

        try {
            // Build search conditions
            SearchConditionRequest condition = new SearchConditionRequest();
            List<Condition> conditions = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                // Search in name and description fields
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

            condition.setConditions(conditions);

            // Get products with pagination
            List<EntityResponse<Product>> productResponses;
            if (condition.getConditions().isEmpty()) {
                productResponses = entityService.getItems(Product.class, pageSize, page, null);
            } else {
                productResponses = entityService.getItemsByCondition(Product.class, condition, false);
            }

            // Convert to slim DTOs for list view
            List<Map<String, Object>> slimProducts = new ArrayList<>();
            for (EntityResponse<Product> response : productResponses) {
                Product product = response.getData();
                Map<String, Object> slimProduct = createSlimProductDto(product);
                slimProducts.add(slimProduct);
            }

            // Apply pagination to results if needed
            int totalElements = slimProducts.size();
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);
            
            List<Map<String, Object>> paginatedProducts = slimProducts.subList(startIndex, endIndex);
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("content", paginatedProducts);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);

            logger.info("Found {} products, returning page {} of {}", totalElements, page, totalPages);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to search products: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get full product details by SKU
     */
    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        logger.info("Getting product details for SKU: {}", sku);

        try {
            EntityResponse<Product> response = entityService.findByBusinessId(Product.class, sku);
            Product product = response.getData();
            
            if (product == null) {
                logger.warn("Product not found for SKU: {}", sku);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found product: SKU={}, name={}", product.getSku(), product.getName());
            return ResponseEntity.ok(product);

        } catch (Exception e) {
            logger.error("Failed to get product by SKU {}: {}", sku, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create slim product DTO for list views
     */
    private Map<String, Object> createSlimProductDto(Product product) {
        Map<String, Object> slimProduct = new HashMap<>();
        slimProduct.put("sku", product.getSku());
        slimProduct.put("name", product.getName());
        slimProduct.put("description", product.getDescription());
        slimProduct.put("price", product.getPrice());
        slimProduct.put("quantityAvailable", product.getQuantityAvailable());
        slimProduct.put("category", product.getCategory());
        
        // Extract image URL from media if available
        String imageUrl = null;
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            imageUrl = product.getMedia().get(0).getUrl();
        }
        slimProduct.put("imageUrl", imageUrl);
        
        return slimProduct;
    }
}
