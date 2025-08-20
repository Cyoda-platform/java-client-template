package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/entity/Product")
@Tag(name = "Product")
public class ProductController {
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Product", description = "Create a new Product. requestId required for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody CreateProductRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            // convert request to ObjectNode to pass through to entity service
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error creating product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Product", description = "Retrieve Product by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getProduct(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(node);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Products", description = "List all Products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Product.class))))
    })
    @GetMapping
    public ResponseEntity<?> listProducts() {
        try {
            ArrayNode arr = entityService.getItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION)
            ).get();
            return ResponseEntity.ok(arr);
        } catch (Exception e) {
            logger.error("Error listing products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Query Products", description = "Query Products by condition (in-memory)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Product.class))))
    })
    @PostMapping("/query")
    public ResponseEntity<?> queryProducts(@RequestBody SearchConditionRequest condition) {
        try {
            ArrayNode arr = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
            ).get();
            return ResponseEntity.ok(arr);
        } catch (Exception e) {
            logger.error("Error querying products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Product", description = "Update a Product by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class)))
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateProduct(@PathVariable String technicalId, @RequestBody CreateProductRequest request) {
        try {
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            UUID updated = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error updating product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Product", description = "Delete a Product by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteProduct(@PathVariable String technicalId) {
        try {
            UUID deleted = entityService.deleteItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error deleting product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class CreateProductRequest {
        @Schema(description = "Idempotency request id", required = true)
        private String requestId;

        @Schema(description = "SKU of the product")
        private String sku;
        @Schema(description = "Name")
        private String name;
        @Schema(description = "Description")
        private String description;
        @Schema(description = "Price")
        private Double price;
        @Schema(description = "Quantity available")
        private Integer quantityAvailable;
        @Schema(description = "Category")
        private String category;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created resource")
        private String technicalId;
    }
}
