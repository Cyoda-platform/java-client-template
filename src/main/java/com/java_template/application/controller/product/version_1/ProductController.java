package com.java_template.application.controller.product.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.*;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Product Controller", description = "APIs to retrieve Product entities (read-only). Controller proxies requests to EntityService.")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Product by technicalId", description = "Retrieve a Product by its technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProductById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.notFound().build();
            }

            ProductResponse response = objectMapper.treeToValue(dataPayload.getData(), ProductResponse.class);

            // try to extract technicalId from meta if present
            JsonNode meta = dataPayload.getMeta();
            if (meta != null && meta.has("entityId")) {
                response.setTechnicalId(meta.get("entityId").asText());
            } else {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getProductById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving product {}: {}", technicalId, ee.getMessage(), ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving product {}: {}", technicalId, ie.getMessage(), ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Error while retrieving product {}: {}", technicalId, e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get all Products", description = "Retrieve all Product entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllProducts() {
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
                    if (payload == null || payload.getData() == null) {
                        continue;
                    }
                    try {
                        ProductResponse response = objectMapper.treeToValue(payload.getData(), ProductResponse.class);
                        JsonNode meta = payload.getMeta();
                        if (meta != null && meta.has("entityId")) {
                            response.setTechnicalId(meta.get("entityId").asText());
                        }
                        responses.add(response);
                    } catch (Exception convEx) {
                        logger.warn("Failed to convert payload to ProductResponse: {}", convEx.getMessage(), convEx);
                        // skip invalid payloads but continue processing others
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving products: {}", ee.getMessage(), ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving products: {}", ie.getMessage(), ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Error while retrieving products: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Data
    @Schema(name = "ProductResponse", description = "Product response payload")
    public static class ProductResponse {
        @Schema(description = "Technical ID (UUID) of the stored entity")
        private String technicalId;

        @Schema(description = "Product business id/reference")
        private String productId;

        @Schema(description = "Product name")
        private String name;

        @Schema(description = "Product category")
        private String category;

        @Schema(description = "Price of the product")
        private Double price;

        @Schema(description = "Arbitrary JSON metadata as string")
        private String metadata;
    }
}