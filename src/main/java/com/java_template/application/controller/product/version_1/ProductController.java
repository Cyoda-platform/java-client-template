package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Tags;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/products/v1")
@Tag(name = "Product", description = "Product entity proxy controller (version 1)")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Product", description = "Persist a Product entity and trigger its workflow. Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Product payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class))
            )
            @RequestBody ProductRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            Product product = new Product();
            // map fields - controller must not implement business logic, only direct mapping
            product.setId(request.getId());
            product.setName(request.getName());
            product.setSku(request.getSku());
            product.setPrice(request.getPrice());
            product.setCurrency(request.getCurrency());
            product.setDescription(request.getDescription());
            product.setAvailable(request.getAvailable());
            product.setStock(request.getStock());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    product
            );

            UUID technicalId = idFuture.get();

            CreateProductResponse response = new CreateProductResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating product: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createProduct execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createProduct execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during createProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating product", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Product", description = "Retrieve a Product by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            UUID techUuid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    techUuid
            );

            ObjectNode node = itemFuture.get();

            ProductResponse response = objectMapper.treeToValue(node, ProductResponse.class);
            response.setTechnicalId(technicalId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getProduct: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Product not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getProduct execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during getProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving product", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for request/response payloads

    @Data
    public static class CreateProductResponse {
        @Schema(description = "Technical ID of the created product", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    public static class ProductRequest {
        @Schema(description = "Business id", example = "P-123")
        private String id;

        @Schema(description = "Display name", example = "T-Shirt")
        private String name;

        @Schema(description = "Stock keeping unit", example = "TS-001")
        private String sku;

        @Schema(description = "Unit price", example = "19.99")
        private Double price;

        @Schema(description = "Currency code", example = "USD")
        private String currency;

        @Schema(description = "Description", example = "Cotton tee")
        private String description;

        @Schema(description = "Sellable flag", example = "true")
        private Boolean available;

        @Schema(description = "Inventory count", example = "100")
        private Integer stock;
    }

    @Data
    public static class ProductResponse {
        @Schema(description = "Technical ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Business id", example = "P-123")
        private String id;

        @Schema(description = "Display name", example = "T-Shirt")
        private String name;

        @Schema(description = "Stock keeping unit", example = "TS-001")
        private String sku;

        @Schema(description = "Unit price", example = "19.99")
        private Double price;

        @Schema(description = "Currency code", example = "USD")
        private String currency;

        @Schema(description = "Description", example = "Cotton tee")
        private String description;

        @Schema(description = "Sellable flag", example = "true")
        private Boolean available;

        @Schema(description = "Inventory count", example = "100")
        private Integer stock;
    }
}