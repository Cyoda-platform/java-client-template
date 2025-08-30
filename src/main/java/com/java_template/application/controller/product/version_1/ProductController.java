package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.product.version_1.Product;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/ui/products")
@Tag(name = "Product Controller", description = "Proxy controller for Product entity (version 1)")
public class ProductController {
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProductController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Product", description = "Persist a Product entity. Returns only technicalId.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PostMapping
    public ResponseEntity<?> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Product payload", content = @Content(schema = @Schema(implementation = ProductRequest.class)))
            @RequestBody ProductRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Product entity = new Product();
            entity.setSku(request.getSku());
            entity.setName(request.getName());
            entity.setDescription(request.getDescription());
            entity.setPrice(request.getPrice());
            entity.setQuantityAvailable(request.getQuantityAvailable());
            entity.setCategory(request.getCategory());
            entity.setWarehouseId(request.getWarehouseId());
            entity.setMedia(request.getMedia());
            entity.setBundles(request.getBundles());
            entity.setVariants(request.getVariants());
            entity.setEvents(request.getEvents());
            entity.setAttributes(request.getAttributes());
            entity.setCompliance(request.getCompliance());
            entity.setInventory(request.getInventory());
            entity.setOptions(request.getOptions());
            entity.setRelationships(request.getRelationships());
            entity.setLocalizations(request.getLocalizations());

            CompletableFuture<UUID> idFuture = entityService.addItem(Product.ENTITY_NAME, Product.ENTITY_VERSION, entity);
            UUID entityId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request when creating product", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating product", ee);
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

    @Operation(summary = "List Products", description = "Retrieve list of products (slim DTO). Supported query filters: search, category, minPrice, maxPrice, page, pageSize")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductSlimResponse.class)))
    @GetMapping
    public ResponseEntity<?> listProducts(
            @Parameter(description = "Search term (matches name)") @RequestParam(value = "search", required = false) String search,
            @Parameter(description = "Category filter") @RequestParam(value = "category", required = false) String category,
            @Parameter(description = "Minimum price") @RequestParam(value = "minPrice", required = false) Double minPrice,
            @Parameter(description = "Maximum price") @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @Parameter(description = "Page number (not implemented server-side, retained for API)") @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size (not implemented server-side, retained for API)") @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        try {
            List<DataPayload> dataPayloads;
            boolean anyFilter = (search != null && !search.isBlank()) ||
                                (category != null && !category.isBlank()) ||
                                (minPrice != null) || (maxPrice != null);

            if (anyFilter) {
                List<Condition> conditions = new ArrayList<>();
                if (search != null && !search.isBlank()) {
                    conditions.add(Condition.of("$.name", "IEQUALS", search));
                }
                if (category != null && !category.isBlank()) {
                    conditions.add(Condition.of("$.category", "EQUALS", category));
                }
                if (minPrice != null) {
                    conditions.add(Condition.of("$.price", "GREATER_THAN", minPrice.toString()));
                }
                if (maxPrice != null) {
                    conditions.add(Condition.of("$.price", "LESS_THAN", maxPrice.toString()));
                }
                SearchConditionRequest condition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
                dataPayloads = filteredItemsFuture.get();
            } else {
                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(Product.ENTITY_NAME, Product.ENTITY_VERSION, null, null, null);
                dataPayloads = itemsFuture.get();
            }

            List<ProductSlimResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload != null ? payload.getData() : null;
                    if (data != null) {
                        Product product = objectMapper.treeToValue(data, Product.class);
                        ProductSlimResponse slim = new ProductSlimResponse();
                        slim.setSku(product.getSku());
                        slim.setName(product.getName());
                        slim.setPrice(product.getPrice());
                        slim.setCategory(product.getCategory());
                        slim.setQuantityAvailable(product.getQuantityAvailable());
                        responses.add(slim);
                    }
                }
            }
            return ResponseEntity.ok(responses);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for listing products", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while listing products", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing products", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while listing products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Product by SKU", description = "Retrieve full product document by SKU")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductFullResponse.class)))
    @GetMapping("/{sku}")
    public ResponseEntity<?> getProductBySku(
            @Parameter(name = "sku", description = "Product SKU") @PathVariable("sku") String sku
    ) {
        try {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku path variable is required");
            }

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.sku", "EQUALS", sku)
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
            List<DataPayload> dataPayloads = filteredItemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                throw new NoSuchElementException("Product not found for sku: " + sku);
            }

            DataPayload payload = dataPayloads.get(0);
            JsonNode data = payload != null ? payload.getData() : null;
            if (data == null) {
                throw new NoSuchElementException("Product data missing for sku: " + sku);
            }

            Product product = objectMapper.treeToValue(data, Product.class);

            ProductFullResponse resp = new ProductFullResponse();
            resp.setSku(product.getSku());
            resp.setName(product.getName());
            resp.setDescription(product.getDescription());
            resp.setPrice(product.getPrice());
            resp.setQuantityAvailable(product.getQuantityAvailable());
            resp.setCategory(product.getCategory());
            resp.setWarehouseId(product.getWarehouseId());
            resp.setMedia(product.getMedia());
            resp.setBundles(product.getBundles());
            resp.setVariants(product.getVariants());
            resp.setEvents(product.getEvents());
            resp.setAttributes(product.getAttributes());
            resp.setCompliance(product.getCompliance());
            resp.setInventory(product.getInventory());
            resp.setOptions(product.getOptions());
            resp.setRelationships(product.getRelationships());
            resp.setLocalizations(product.getLocalizations());

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for getting product by sku", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while getting product by sku", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting product by sku", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (NoSuchElementException nse) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(nse.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while getting product by sku", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for requests/responses

    @Data
    @Schema(name = "ProductRequest", description = "Request payload to create a Product")
    public static class ProductRequest {
        private String name;
        private String sku;
        private Double price;
        private Integer quantityAvailable;
        private String category;
        private String description;
        private String warehouseId;
        private List<String> media;
        private List<java.util.Map<String, Object>> bundles;
        private List<java.util.Map<String, Object>> variants;
        private List<Object> events;
        private java.util.Map<String, Object> attributes;
        private java.util.Map<String, Object> compliance;
        private java.util.Map<String, Object> inventory;
        private java.util.Map<String, Object> options;
        private java.util.Map<String, Object> relationships;
        private java.util.Map<String, Product.Localization> localizations;
    }

    @Data
    @Schema(name = "ProductFullResponse", description = "Full Product document as persisted")
    public static class ProductFullResponse {
        private String name;
        private String sku;
        private Double price;
        private Integer quantityAvailable;
        private String category;
        private String description;
        private String warehouseId;
        private List<String> media;
        private List<java.util.Map<String, Object>> bundles;
        private List<java.util.Map<String, Object>> variants;
        private List<Object> events;
        private java.util.Map<String, Object> attributes;
        private java.util.Map<String, Object> compliance;
        private java.util.Map<String, Object> inventory;
        private java.util.Map<String, Object> options;
        private java.util.Map<String, Object> relationships;
        private java.util.Map<String, Product.Localization> localizations;
    }

    @Data
    @Schema(name = "ProductSlimResponse", description = "Slim product representation for lists")
    public static class ProductSlimResponse {
        private String sku;
        private String name;
        private Double price;
        private String category;
        private Integer quantityAvailable;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId only")
    public static class TechnicalIdResponse {
        private String technicalId;
    }
}