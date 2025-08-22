package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.product.version_1.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Controller acting as a proxy to EntityService for Product entity (v1).
 * No business logic implemented - only request validation/forwarding and response/error mapping.
 */
@RestController
@RequestMapping("/api/product/v1")
@Tag(name = "Product", description = "Product entity API (version 1)")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create product", description = "Create a single product")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateProductResponse.class)))
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody CreateProductRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Product product = mapToProduct(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    product
            );

            UUID technicalId = idFuture.get();

            CreateProductResponse resp = new CreateProductResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while creating product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Create products (batch)", description = "Create multiple products in batch")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchTechnicalIdResponse.class)))
    @PostMapping("/batch")
    public ResponseEntity<?> createProducts(@RequestBody List<CreateProductRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one product");
            }

            List<Product> products = new ArrayList<>();
            for (CreateProductRequest r : requests) {
                products.add(mapToProduct(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    products
            );

            List<UUID> technicalIds = idsFuture.get();
            BatchTechnicalIdResponse resp = new BatchTechnicalIdResponse();
            List<String> ids = new ArrayList<>();
            for (UUID u : technicalIds) {
                ids.add(u.toString());
            }
            resp.setTechnicalIds(ids);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating products", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while creating products", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Get product by technicalId", description = "Retrieve a single product by technical id")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductGetResponse.class)))
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode item = itemFuture.get();
            ProductGetResponse resp = new ProductGetResponse();
            resp.setTechnicalId(technicalId);
            resp.setProduct(item);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid input: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Get all products", description = "Retrieve all products")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    @GetMapping
    public ResponseEntity<?> getProducts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);

        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving products", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving products", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Search products by condition", description = "Search products using simple conditions")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    @PostMapping("/search")
    public ResponseEntity<?> searchProducts(@RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search request: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching products", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while searching products", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Update product", description = "Update a product by technical id")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @RequestBody ProductRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Product product = mapToProduct(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    product
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid update request: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while updating product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Delete product", description = "Delete a product by technical id")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid delete request: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting product", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage(), cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", cause.getMessage()));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid operation: {}", cause.getMessage(), cause);
            return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
        } else {
            logger.error("Execution exception: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    private Product mapToProduct(CreateProductRequest request) {
        Product p = new Product();
        // Only shallow mapping - controller must not implement business logic.
        p.setId(request.getId());
        p.setName(request.getName());
        p.setSku(request.getSku());
        p.setPrice(request.getPrice());
        p.setCurrency(request.getCurrency());
        p.setDescription(request.getDescription());
        p.setAvailable(request.getAvailable());
        p.setStock(request.getStock());
        return p;
    }

    private Product mapToProduct(ProductRequest request) {
        Product p = new Product();
        p.setId(request.getId());
        p.setName(request.getName());
        p.setSku(request.getSku());
        p.setPrice(request.getPrice());
        p.setCurrency(request.getCurrency());
        p.setDescription(request.getDescription());
        p.setAvailable(request.getAvailable());
        p.setStock(request.getStock());
        return p;
    }

    // DTOs

    @Data
    @Schema(name = "CreateProductRequest", description = "Request to create a product")
    public static class CreateProductRequest {
        @Schema(description = "Business ID of the product", example = "PRD-001")
        private String id;
        @Schema(description = "Product name", example = "Blue T-Shirt")
        private String name;
        @Schema(description = "SKU", example = "TSHIRT-BLUE-001")
        private String sku;
        @Schema(description = "Price", example = "19.99")
        private Double price;
        @Schema(description = "Currency", example = "USD")
        private String currency;
        @Schema(description = "Description")
        private String description;
        @Schema(description = "Available flag")
        private Boolean available;
        @Schema(description = "Stock quantity", example = "100")
        private Integer stock;
    }

    @Data
    @Schema(name = "CreateProductResponse", description = "Response after creating a product")
    public static class CreateProductResponse {
        @Schema(description = "Technical id (UUID) of created entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;
    }

    @Data
    @Schema(name = "ProductRequest", description = "Request DTO for updating or creating a product")
    public static class ProductRequest {
        @Schema(description = "Business ID of the product", example = "PRD-001")
        private String id;
        @Schema(description = "Product name", example = "Blue T-Shirt")
        private String name;
        @Schema(description = "SKU", example = "TSHIRT-BLUE-001")
        private String sku;
        @Schema(description = "Price", example = "19.99")
        private Double price;
        @Schema(description = "Currency", example = "USD")
        private String currency;
        @Schema(description = "Description")
        private String description;
        @Schema(description = "Available flag")
        private Boolean available;
        @Schema(description = "Stock quantity", example = "100")
        private Integer stock;
    }

    @Data
    @Schema(name = "ProductGetResponse", description = "Response containing product payload")
    public static class ProductGetResponse {
        @Schema(description = "Technical id (UUID) of the entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;

        @Schema(description = "Product payload as JSON object")
        private ObjectNode product;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response with technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchTechnicalIdResponse", description = "Response with list of technical ids")
    public static class BatchTechnicalIdResponse {
        @Schema(description = "List of technical ids (UUIDs)")
        private List<String> technicalIds;
    }
}