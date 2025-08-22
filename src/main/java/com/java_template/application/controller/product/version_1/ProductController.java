package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Product API", description = "API for Product entity (version 1) - controller proxies to EntityService")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;

    public ProductController(final EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Product", description = "Creates a new Product entity. Returns technicalId only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product payload", required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class)))
            @RequestBody ProductRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Product product = mapToEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    product
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create product: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating product", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Products", description = "Creates multiple Product entities. Returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createProductsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Product payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductRequest.class))))
            @RequestBody List<ProductRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list is required and must not be empty");
            }

            List<Product> entities = new ArrayList<>();
            for (ProductRequest r : requests) {
                entities.add(mapToEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = new ArrayList<>();
            for (UUID id : ids) {
                technicalIds.add(id.toString());
            }
            TechnicalIdsResponse resp = new TechnicalIdsResponse(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid bulk create request for products: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while bulk creating products", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk creating products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while bulk creating products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Product by technicalId", description = "Retrieves a Product entity by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductGetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getProductById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    tid
            );

            ObjectNode entityNode = itemFuture.get();
            ProductGetResponse resp = new ProductGetResponse(technicalId, entityNode);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid get request for product {}: {}", technicalId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving product {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving product {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving product {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Products", description = "Retrieves all Product entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllProducts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            ProductsListResponse resp = new ProductsListResponse(items);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving all products", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving all products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Products by condition", description = "Retrieves Product entities filtered by provided condition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchProducts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
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
            ProductsListResponse resp = new ProductsListResponse(items);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request for products: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching products", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while searching products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Product", description = "Updates an existing Product by technicalId. Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product payload", required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class)))
            @RequestBody ProductRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            UUID tid = UUID.fromString(technicalId);
            Product product = mapToEntity(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    tid,
                    product
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid update request for product {}: {}", technicalId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating product {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating product {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while updating product {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Product", description = "Deletes a Product by technicalId. Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    tid
            );

            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delete request for product {}: {}", technicalId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting product {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting product {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while deleting product {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private Product mapToEntity(ProductRequest request) {
        Product product = new Product();
        product.setId(request.getId());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCurrency(request.getCurrency());
        product.setAvailableQuantity(request.getAvailableQuantity());
        return product;
    }

    @Data
    @Schema(name = "ProductRequest", description = "Product creation/update request")
    public static class ProductRequest {
        @Schema(description = "Business id (e.g., SKU)", example = "sku-123")
        private String id;

        @Schema(description = "Display name", example = "Tote")
        private String name;

        @Schema(description = "Marketing text", example = "Reusable tote")
        private String description;

        @Schema(description = "Unit price", example = "19.99")
        private BigDecimal price;

        @Schema(description = "ISO currency", example = "USD")
        private String currency;

        @Schema(description = "Available quantity in stock", example = "100")
        private Integer availableQuantity;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing list of technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "ProductGetResponse", description = "Product retrieval response including technicalId and entity")
    public static class ProductGetResponse {
        @Schema(description = "Technical id of the entity")
        private String technicalId;

        @Schema(description = "Entity JSON")
        private ObjectNode entity;

        public ProductGetResponse(String technicalId, ObjectNode entity) {
            this.technicalId = technicalId;
            this.entity = entity;
        }
    }

    @Data
    @Schema(name = "ProductsListResponse", description = "List response for product entities")
    public static class ProductsListResponse {
        @Schema(description = "Array of entities")
        private ArrayNode items;

        public ProductsListResponse(ArrayNode items) {
            this.items = items;
        }
    }
}