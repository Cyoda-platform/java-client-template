package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(path = "/api/product/v1")
@Tag(name = "Product API", description = "API for managing Product entities (v1)")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create product", description = "Creates a single product entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "", produces = "application/json", consumes = "application/json")
    public ResponseEntity<?> addProduct(@RequestBody ProductRequest request) {
        try {
            Product product = mapToEntity(request);
            ArrayNode data = objectMapper.valueToTree(product);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.getTechnicalIds().add(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid input for addProduct: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (Exception ex) {
            logger.error("Unexpected error in addProduct", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple products", description = "Creates multiple product entities in bulk")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", produces = "application/json", consumes = "application/json")
    public ResponseEntity<?> addProducts(@RequestBody BulkProductRequest request) {
        try {
            List<Product> list = new ArrayList<>();
            if (request.getProducts() != null) {
                for (ProductRequest pr : request.getProducts()) {
                    list.add(mapToEntity(pr));
                }
            }
            ArrayNode data = objectMapper.valueToTree(list);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    data
            );
            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            for (UUID id : ids) resp.getTechnicalIds().add(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid input for addProducts: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (Exception ex) {
            logger.error("Unexpected error in addProducts", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get product by technical id", description = "Retrieves a product entity by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = "application/json", consumes = "*/*")
    public ResponseEntity<?> getProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId in getProduct: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (Exception ex) {
            logger.error("Unexpected error in getProduct", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all products", description = "Retrieves all product entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "", produces = "application/json", consumes = "*/*")
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
        } catch (Exception ex) {
            logger.error("Unexpected error in getProducts", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search products", description = "Search products by a provided search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", produces = "application/json", consumes = "application/json")
    public ResponseEntity<?> searchProducts(@RequestBody SearchConditionRequest conditionRequest) {
        try {
            // Delegate to entityService - inMemory true as per instructions for simple filtering
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    conditionRequest,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search condition in searchProducts: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (Exception ex) {
            logger.error("Unexpected error in searchProducts", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update product", description = "Updates a product entity by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", produces = "application/json", consumes = "application/json")
    public ResponseEntity<?> updateProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody ProductRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            Product product = mapToEntity(request);
            ArrayNode data = objectMapper.valueToTree(product);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    id,
                    data
            );
            UUID updated = updatedIdFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.getTechnicalIds().add(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid input in updateProduct: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (Exception ex) {
            logger.error("Unexpected error in updateProduct", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete product", description = "Deletes a product entity by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = "application/json", consumes = "*/*")
    public ResponseEntity<?> deleteProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    id
            );
            UUID deleted = deletedIdFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.getTechnicalIds().add(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId in deleteProduct: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteProduct", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    private ResponseEntity<String> handleExecutionException(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(404).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution exception with unexpected cause", ex);
            return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
        }
    }

    private Product mapToEntity(ProductRequest req) {
        Product p = new Product();
        // Map fields if present. Keep mapping minimal (controller must not implement business logic)
        p.setId(req.getId());
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setCurrency(req.getCurrency());
        p.setAvailableQuantity(req.getAvailableQuantity());
        p.setCreatedAt(req.getCreatedAt());
        p.setUpdatedAt(req.getUpdatedAt());
        return p;
    }

    // Static DTO classes

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductRequest {
        @Schema(description = "Technical id (UUID string)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String id;

        @Schema(description = "Product name", example = "T-shirt")
        private String name;

        @Schema(description = "Product description", example = "100% cotton t-shirt")
        private String description;

        @Schema(description = "Price of the product", example = "19.99")
        private Double price;

        @Schema(description = "Currency code", example = "USD")
        private String currency;

        @Schema(description = "Available quantity", example = "100")
        private Integer availableQuantity;

        @Schema(description = "Created at timestamp", example = "2025-01-01T12:00:00Z")
        private String createdAt;

        @Schema(description = "Updated at timestamp", example = "2025-01-02T12:00:00Z")
        private String updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkProductRequest {
        @Schema(description = "List of products to create")
        private List<ProductRequest> products = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical ids (UUID strings)")
        private List<String> technicalIds = new ArrayList<>();
    }
}