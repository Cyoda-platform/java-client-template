package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product Controller", description = "Proxy controller for Product entity operations (version 1)")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Product", description = "Persist a Product entity. Returns technicalId of created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<?> createProduct(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product payload", required = true,
            content = @Content(schema = @Schema(implementation = ProductRequest.class)))
                                           @RequestBody ProductRequest request) {
        try {
            Product product = toProductEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    product
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in createProduct", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("Execution exception in createProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createProduct", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in createProduct", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Create multiple Products", description = "Persist multiple Product entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchTechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createProductsBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch products payload", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductRequest.class))))
                                               @RequestBody BatchProductRequest request) {
        try {
            List<Product> products = new ArrayList<>();
            if (request.getProducts() != null) {
                for (ProductRequest pr : request.getProducts()) {
                    products.add(toProductEntity(pr));
                }
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    products
            );
            List<UUID> ids = idsFuture.get();
            List<String> strIds = new ArrayList<>();
            for (UUID u : ids) strIds.add(u.toString());
            return ResponseEntity.ok(new BatchTechnicalIdsResponse(strIds));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in createProductsBatch", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("Execution exception in createProductsBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createProductsBatch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in createProductsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get Product by technicalId", description = "Retrieve a Product by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in getProduct", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("Execution exception in getProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getProduct", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in getProduct", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get all Products", description = "Retrieve all Product entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Product.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<?> getAllProducts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION)
            );
            ArrayNode nodes = itemsFuture.get();
            return ResponseEntity.ok(nodes);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution exception in getAllProducts", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAllProducts", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in getAllProducts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Search Products", description = "Search Products by a single field condition. Provide fieldName, operator and value as query params.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Product.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(
            @RequestParam String fieldName,
            @RequestParam String operator,
            @RequestParam String value
    ) {
        try {
            String jsonPath = "$." + fieldName;
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of(jsonPath, operator, value)
            );
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode result = filteredFuture.get();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in searchProducts", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution exception in searchProducts", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in searchProducts", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in searchProducts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Update Product", description = "Update a Product entity by technicalId. Returns technicalId of updated entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product payload", required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class)))
            @RequestBody ProductRequest request) {
        try {
            Product product = toProductEntity(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    product
            );
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in updateProduct", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("Execution exception in updateProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updateProduct", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in updateProduct", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Delete Product", description = "Delete a Product by technicalId. Returns technicalId of deleted entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in deleteProduct", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("Execution exception in deleteProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in deleteProduct", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in deleteProduct", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    private Product toProductEntity(ProductRequest req) {
        Product p = new Product();
        p.setSku(req.getSku());
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setAvailableQuantity(req.getAvailableQuantity());
        p.setActive(req.getActive());
        p.setCreatedAt(req.getCreatedAt());
        return p;
    }

    // Static DTO classes

    @Data
    @Schema(name = "ProductRequest", description = "Request payload for Product")
    public static class ProductRequest {
        @Schema(description = "Unique product SKU", example = "SKU-123")
        private String sku;

        @Schema(description = "Product name", example = "Sample Product")
        private String name;

        @Schema(description = "Short description")
        private String description;

        @Schema(description = "Unit price")
        private Double price;

        @Schema(description = "Available quantity")
        private Integer availableQuantity;

        @Schema(description = "Active flag")
        private Boolean active;

        @Schema(description = "Created at ISO timestamp")
        private String createdAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID of entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BatchProductRequest", description = "Request payload for batch product creation")
    public static class BatchProductRequest {
        @Schema(description = "List of products")
        private List<ProductRequest> products;
    }

    @Data
    @Schema(name = "BatchTechnicalIdsResponse", description = "Response containing list of technical ids")
    public static class BatchTechnicalIdsResponse {
        @Schema(description = "List of technical UUIDs")
        private List<String> technicalIds;

        public BatchTechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "ErrorResponse", description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}