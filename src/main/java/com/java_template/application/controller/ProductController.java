package com.java_template.application.controller;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ui/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        logger.info("Getting products with search={}, category={}, minPrice={}, maxPrice={}, page={}, pageSize={}",
            search, category, minPrice, maxPrice, page, pageSize);

        try {
            // Build search conditions
            List<Condition> conditions = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                // Search in name or description
                Condition nameCondition = Condition.of("$.name", "CONTAINS", search);
                Condition descCondition = Condition.of("$.description", "CONTAINS", search);
                // Note: This is a simplified approach. In a real implementation,
                // you'd use OR conditions properly
                conditions.add(nameCondition);
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

            SearchConditionRequest condition = null;
            if (!conditions.isEmpty()) {
                condition = new SearchConditionRequest();
                condition.setType("group");
                condition.setOperator("AND");
                condition.setConditions(conditions);
            }

            CompletableFuture<List<org.cyoda.cloud.api.event.common.DataPayload>> future;
            if (condition != null) {
                future = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    condition,
                    true
                );
            } else {
                future = entityService.getItems(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    pageSize,
                    page,
                    null
                );
            }

            return future.thenApply(dataPayloads -> {
                List<Map<String, Object>> slimProducts = new ArrayList<>();

                for (org.cyoda.cloud.api.event.common.DataPayload payload : dataPayloads) {
                    try {
                        Product product = objectMapper.convertValue(payload.getData(), Product.class);

                        // Create slim DTO
                        Map<String, Object> slimProduct = new HashMap<>();
                        slimProduct.put("sku", product.getSku());
                        slimProduct.put("name", product.getName());
                        slimProduct.put("description", product.getDescription());
                        slimProduct.put("price", product.getPrice());
                        slimProduct.put("quantityAvailable", product.getQuantityAvailable());
                        slimProduct.put("category", product.getCategory());

                        // Add imageUrl if available from media
                        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
                            product.getMedia().stream()
                                .filter(media -> "image".equals(media.getType()))
                                .findFirst()
                                .ifPresent(media -> slimProduct.put("imageUrl", media.getUrl()));
                        }

                        slimProducts.add(slimProduct);
                    } catch (Exception e) {
                        logger.error("Error converting product data: {}", e.getMessage(), e);
                    }
                }

                // Apply pagination manually for now (in a real implementation, this would be done at the query level)
                int start = page * pageSize;
                int end = Math.min(start + pageSize, slimProducts.size());
                List<Map<String, Object>> paginatedProducts = slimProducts.subList(
                    Math.min(start, slimProducts.size()),
                    Math.min(end, slimProducts.size())
                );

                Map<String, Object> response = new HashMap<>();
                response.put("content", paginatedProducts);
                response.put("page", page);
                response.put("pageSize", pageSize);
                response.put("totalElements", slimProducts.size());
                response.put("totalPages", (int) Math.ceil((double) slimProducts.size() / pageSize));

                return ResponseEntity.ok(response);
            }).exceptionally(throwable -> {
                logger.error("Error getting products: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in getProducts: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @GetMapping("/{sku}")
    public CompletableFuture<ResponseEntity<Product>> getProductBySku(@PathVariable String sku) {
        logger.info("Getting product by SKU: {}", sku);

        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", skuCondition);

            return entityService.getFirstItemByCondition(
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                condition,
                true
            ).thenApply(optionalPayload -> {
                if (optionalPayload.isPresent()) {
                    try {
                        Product product = objectMapper.convertValue(optionalPayload.get().getData(), Product.class);
                        return ResponseEntity.ok(product);
                    } catch (Exception e) {
                        logger.error("Error converting product data: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Product>build();
                    }
                } else {
                    return ResponseEntity.notFound().<Product>build();
                }
            }).exceptionally(throwable -> {
                logger.error("Error getting product by SKU {}: {}", sku, throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in getProductBySku: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }
}