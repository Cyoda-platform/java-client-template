package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for Product entity. All business logic is implemented in workflows.
 */
@RestController
@RequestMapping("/api/v1/products")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Product", description = "Product entity proxy API")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Product by technicalId", description = "Retrieve a Product by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProductById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                id
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            ProductResponse response = objectMapper.treeToValue(node, ProductResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in getProductById: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getProductById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getProductById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getProductById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Products", description = "Retrieve all Product entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllProducts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<ProductResponse> list = new ArrayList<>();
            if (array != null) {
                for (JsonNode node : array) {
                    ProductResponse resp = objectMapper.treeToValue(node, ProductResponse.class);
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            logger.error("ExecutionException in getAllProducts", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAllProducts", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllProducts", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Products by condition", description = "Search Products using SearchConditionRequest. Use SearchConditionRequest.group(...) and Condition.of(...) to build queries.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchProducts(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload") @RequestBody @Valid SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode array = filteredItemsFuture.get();
            List<ProductResponse> list = new ArrayList<>();
            if (array != null) {
                for (JsonNode node : array) {
                    ProductResponse resp = objectMapper.treeToValue(node, ProductResponse.class);
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in searchProducts: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            logger.error("ExecutionException in searchProducts", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in searchProducts", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in searchProducts", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add a Product", description = "Add (create) a Product entity. Note: In typical flow Products are created by ingestion workflows; this endpoint proxies to EntityService.addItem.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addProduct(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product payload") @RequestBody @Valid ProductRequest request) {
        try {
            Product entity = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                entity
            );
            UUID createdId = idFuture.get();
            CreateResponse resp = new CreateResponse(createdId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in addProduct: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addProduct", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in addProduct", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in addProduct", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add multiple Products", description = "Add multiple Product entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addProductsBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Product payloads") @RequestBody @Valid List<ProductRequest> requests) {
        try {
            List<Product> entities = new ArrayList<>();
            if (requests != null) {
                for (ProductRequest req : requests) {
                    entities.add(toEntity(req));
                }
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                entities
            );
            List<UUID> ids = idsFuture.get();
            List<CreateResponse> responses = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) {
                    responses.add(new CreateResponse(id.toString()));
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in addProductsBulk: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            logger.error("ExecutionException in addProductsBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in addProductsBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in addProductsBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update a Product", description = "Update a Product by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProduct(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product payload") @RequestBody @Valid ProductRequest request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            Product entity = toEntity(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                id,
                entity
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new CreateResponse(updatedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in updateProduct: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateProduct", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updateProduct", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in updateProduct", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete a Product", description = "Delete a Product by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteProduct(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new CreateResponse(deletedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in deleteProduct: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteProduct", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in deleteProduct", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteProduct", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Conversion helpers
    private Product toEntity(ProductRequest req) {
        if (req == null) throw new IllegalArgumentException("ProductRequest is null");
        Product p = new Product();
        p.setProductId(req.getProductId());
        p.setName(req.getName());
        p.setCategory(req.getCategory());
        p.setPrice(req.getPrice());
        p.setCost(req.getCost());
        p.setTotalRevenue(req.getTotalRevenue());
        p.setInventoryOnHand(req.getInventoryOnHand());
        p.setTotalSalesVolume(req.getTotalSalesVolume());
        p.setPerformanceFlag(req.getPerformanceFlag());
        p.setLastUpdated(req.getLastUpdated());
        return p;
    }

    // DTOs
    @Data
    @Schema(name = "ProductRequest", description = "Product request payload")
    public static class ProductRequest {
        @JsonProperty("product_id")
        @Schema(description = "Product identifier from source", required = true, example = "123")
        private String productId;

        @JsonProperty("name")
        @Schema(description = "Product name", required = true, example = "Dog Toy")
        private String name;

        @JsonProperty("category")
        @Schema(description = "Product category", required = true, example = "Toys")
        private String category;

        @JsonProperty("price")
        @Schema(description = "Product price", required = true, example = "9.99")
        private Double price;

        @JsonProperty("cost")
        @Schema(description = "Product cost", required = true, example = "4.50")
        private Double cost;

        @JsonProperty("total_sales_volume")
        @Schema(description = "Total sales volume", example = "150")
        private Integer totalSalesVolume;

        @JsonProperty("total_revenue")
        @Schema(description = "Total revenue", example = "1498.5")
        private Double totalRevenue;

        @JsonProperty("inventory_on_hand")
        @Schema(description = "Inventory on hand", example = "12")
        private Integer inventoryOnHand;

        @JsonProperty("performance_flag")
        @Schema(description = "Performance flag", example = "RESTOCK")
        private String performanceFlag;

        @JsonProperty("last_updated")
        @Schema(description = "Last updated timestamp", example = "2025-08-25T08:10:00Z")
        private String lastUpdated;
    }

    @Data
    @Schema(name = "ProductResponse", description = "Product response payload")
    public static class ProductResponse {
        @JsonProperty("product_id")
        @Schema(description = "Product identifier from source", example = "123")
        private String productId;

        @JsonProperty("name")
        @Schema(description = "Product name", example = "Dog Toy")
        private String name;

        @JsonProperty("category")
        @Schema(description = "Product category", example = "Toys")
        private String category;

        @JsonProperty("price")
        @Schema(description = "Product price", example = "9.99")
        private Double price;

        @JsonProperty("cost")
        @Schema(description = "Product cost", example = "4.50")
        private Double cost;

        @JsonProperty("total_sales_volume")
        @Schema(description = "Total sales volume", example = "150")
        private Integer totalSalesVolume;

        @JsonProperty("total_revenue")
        @Schema(description = "Total revenue", example = "1498.5")
        private Double totalRevenue;

        @JsonProperty("inventory_on_hand")
        @Schema(description = "Inventory on hand", example = "12")
        private Integer inventoryOnHand;

        @JsonProperty("performance_flag")
        @Schema(description = "Performance flag", example = "RESTOCK")
        private String performanceFlag;

        @JsonProperty("last_updated")
        @Schema(description = "Last updated timestamp", example = "2025-08-25T08:10:00Z")
        private String lastUpdated;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Create response with technicalId")
    public static class CreateResponse {
        @JsonProperty("technicalId")
        @Schema(description = "Technical identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;

        public CreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}