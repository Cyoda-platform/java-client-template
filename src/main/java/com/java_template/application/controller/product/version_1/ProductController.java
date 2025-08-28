package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.product.version_1.Product;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/product")
@Tag(name = "Product", description = "Product entity proxy endpoints (event-driven). Controller is a thin proxy to EntityService.")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProductController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Product", description = "Persist a new Product event. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class)))
            @RequestBody ProductRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is null");
            }

            Product product = new Product();
            // generate technical id (controller responsibility only to prepare entity for persistence)
            product.setId(UUID.randomUUID().toString());
            product.setName(request.getName());
            product.setDescription(request.getDescription());
            product.setSku(request.getSku());
            product.setAvailableQuantity(request.getAvailableQuantity());
            product.setPrice(request.getPrice());
            product.setWarehouseId(request.getWarehouseId());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    product
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createProduct", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error in createProduct", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Products", description = "Persist multiple Product events. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdListResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createProductsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of products to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductRequest.class))))
            @RequestBody List<ProductRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list is null or empty");
            }
            List<Product> entities = new ArrayList<>();
            for (ProductRequest r : requests) {
                Product product = new Product();
                product.setId(UUID.randomUUID().toString());
                product.setName(r.getName());
                product.setDescription(r.getDescription());
                product.setSku(r.getSku());
                product.setAvailableQuantity(r.getAvailableQuantity());
                product.setPrice(r.getPrice());
                product.setWarehouseId(r.getWarehouseId());
                entities.add(product);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    entities
            );
            List<UUID> uuids = idsFuture.get();
            TechnicalIdListResponse resp = new TechnicalIdListResponse();
            List<String> ids = new ArrayList<>();
            if (uuids != null) {
                for (UUID u : uuids) ids.add(u.toString());
            }
            resp.setTechnicalIds(ids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createProductsBatch", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createProductsBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error in createProductsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Product by technicalId", description = "Retrieve stored Product by technical id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getProductById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
            }
            JsonNode node = (JsonNode) dataPayload.getData();
            ProductResponse response = objectMapper.treeToValue(node, ProductResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getProductById request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getProductById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error in getProductById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Products", description = "Retrieve all Products (unpaged).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> listProducts() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<ProductResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        ProductResponse resp = objectMapper.treeToValue(payload.getData(), ProductResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listProducts", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error in listProducts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Query Products by condition", description = "Retrieve Products filtered by a SearchConditionRequest.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/query")
    public ResponseEntity<?> queryProducts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<List<DataPayload>> filteredFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredFuture.get();
            List<ProductResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        ProductResponse resp = objectMapper.treeToValue(payload.getData(), ProductResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid queryProducts request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in queryProducts", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error in queryProducts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Product", description = "Update a Product by technicalId. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product update payload", required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class)))
            @RequestBody ProductRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is null");
            }

            Product product = new Product();
            product.setId(technicalId); // use provided technical id
            product.setName(request.getName());
            product.setDescription(request.getDescription());
            product.setSku(request.getSku());
            product.setAvailableQuantity(request.getAvailableQuantity());
            product.setPrice(request.getPrice());
            product.setWarehouseId(request.getWarehouseId());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(UUID.fromString(technicalId), product);
            UUID updatedId = updatedIdFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateProduct request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error in updateProduct", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Product", description = "Delete a Product by technicalId. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteProduct(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteProduct request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteProduct", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error in deleteProduct", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "ProductRequest", description = "Request payload for creating/updating a Product")
    public static class ProductRequest {
        @Schema(description = "Display name", example = "Blue Widget")
        private String name;

        @Schema(description = "Detailed description", example = "A blue widget for testing")
        private String description;

        @Schema(description = "Catalog SKU", example = "BW-001")
        private String sku;

        @Schema(description = "Available quantity", example = "100")
        private Integer availableQuantity;

        @Schema(description = "Unit price", example = "9.99")
        private Double price;

        @Schema(description = "Primary warehouse id", example = "wh-123")
        private String warehouseId;
    }

    @Data
    @Schema(name = "ProductResponse", description = "Representation of stored Product")
    public static class ProductResponse {
        @Schema(description = "Technical id", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String id;

        @Schema(description = "Display name", example = "Blue Widget")
        private String name;

        @Schema(description = "Detailed description", example = "A blue widget for testing")
        private String description;

        @Schema(description = "Catalog SKU", example = "BW-001")
        private String sku;

        @Schema(description = "Available quantity", example = "100")
        private Integer availableQuantity;

        @Schema(description = "Unit price", example = "9.99")
        private Double price;

        @Schema(description = "Primary warehouse id", example = "wh-123")
        private String warehouseId;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the created/modified/deleted entity", example = "tx-abc-123")
        private String technicalId;
    }

    @Data
    @Schema(name = "TechnicalIdListResponse", description = "Response containing list of technical ids")
    public static class TechnicalIdListResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;
    }
}