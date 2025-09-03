package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
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
import java.util.Optional;

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
            logger.info("Searching products: search={}, category={}, minPrice={}, maxPrice={}, page={}, pageSize={}", 
                search, category, minPrice, maxPrice, page, pageSize);

            // Build search conditions
            List<Condition> conditions = new ArrayList<>();
            
            // Free-text search on name OR description
            if (search != null && !search.trim().isEmpty()) {
                Condition nameCondition = Condition.of("$.name", "CONTAINS", search.trim());
                Condition descCondition = Condition.of("$.description", "CONTAINS", search.trim());
                
                // Create OR condition for name/description search
                SearchConditionRequest searchGroup = new SearchConditionRequest();
                searchGroup.setType("group");
                searchGroup.setOperator("OR");
                searchGroup.setConditions(List.of(nameCondition, descCondition));
                
                // For now, we'll use a simplified approach with name search only
                conditions.add(nameCondition);
            }
            
            // Category filter
            if (category != null && !category.trim().isEmpty()) {
                conditions.add(Condition.of("$.category", "EQUALS", category.trim()));
            }
            
            // Price range filters
            if (minPrice != null) {
                conditions.add(Condition.of("$.price", "GREATER_THAN_OR_EQUAL", minPrice));
            }
            
            if (maxPrice != null) {
                conditions.add(Condition.of("$.price", "LESS_THAN_OR_EQUAL", maxPrice));
            }

            // Create search condition
            SearchConditionRequest condition = null;
            if (!conditions.isEmpty()) {
                condition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            }

            // Get products
            List<EntityResponse<Product>> productResponses;
            if (condition != null) {
                productResponses = entityService.getItemsByCondition(
                    Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
            } else {
                productResponses = entityService.getItems(
                    Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, pageSize, page, null);
            }

            // Convert to slim DTOs for performance
            List<Map<String, Object>> slimProducts = new ArrayList<>();
            for (EntityResponse<Product> response : productResponses) {
                Product product = response.getData();
                Map<String, Object> slimProduct = new HashMap<>();
                slimProduct.put("sku", product.getSku());
                slimProduct.put("name", product.getName());
                slimProduct.put("description", product.getDescription());
                slimProduct.put("price", product.getPrice());
                slimProduct.put("quantityAvailable", product.getQuantityAvailable());
                slimProduct.put("category", product.getCategory());
                
                // Extract image URL from media if available
                if (product.getMedia() != null && !product.getMedia().isEmpty()) {
                    Optional<String> imageUrl = product.getMedia().stream()
                        .filter(media -> "image".equals(media.getType()))
                        .map(Product.Media::getUrl)
                        .findFirst();
                    slimProduct.put("imageUrl", imageUrl.orElse(null));
                } else {
                    slimProduct.put("imageUrl", null);
                }
                
                slimProducts.add(slimProduct);
            }

            // Apply pagination manually (simplified approach)
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

            logger.info("Found {} products, returning page {} with {} items", 
                totalElements, page, paginatedProducts.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        try {
            logger.info("Getting product by SKU: {}", sku);

            // Search for product by SKU
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", skuCondition);

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);

            if (productResponse.isPresent()) {
                Product product = productResponse.get().getData();
                logger.info("Found product: {}", product.getName());
                return ResponseEntity.ok(product);
            } else {
                logger.warn("Product not found: {}", sku);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting product by SKU: {}", sku, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Product>> createProduct(@RequestBody Product product) {
        try {
            logger.info("Creating product: {}", product.getSku());
            
            // CRITICAL: Pass product entity directly - it IS the payload
            EntityResponse<Product> response = entityService.save(product);
            logger.info("Product created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating product", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{sku}")
    public ResponseEntity<EntityResponse<Product>> updateProduct(
            @PathVariable String sku, 
            @RequestBody Product product) {
        try {
            logger.info("Updating product: {}", sku);
            
            // Find product by SKU to get technical ID
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", skuCondition);

            Optional<EntityResponse<Product>> existingResponse = entityService.getFirstItemByCondition(
                Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);

            if (existingResponse.isPresent()) {
                // CRITICAL: Pass product entity directly - no payload manipulation needed
                EntityResponse<Product> response = entityService.update(
                    existingResponse.get().getMetadata().getId(), product, null);
                logger.info("Product updated: {}", sku);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Product not found for update: {}", sku);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error updating product", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
