package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/products")
@Tag(name = "Product Controller", description = "Proxy controller for Product entity operations")
public class ProductController {
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final EntityService entityService;

    public ProductController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Product by technicalId", description = "Retrieve product by internal technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProduct(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) throw new NoSuchElementException("Product not found");
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for getProduct", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERRUPTED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while fetching Product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "List Products", description = "Retrieve all products or filter by simple field condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = ProductResponse.class))))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listProducts(@RequestParam(value = "field", required = false) String field,
                                          @RequestParam(value = "op", required = false, defaultValue = "EQUALS") String op,
                                          @RequestParam(value = "value", required = false) String value) {
        try {
            if (field != null && value != null) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$." + field, op, value)
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        condition,
                        true
                );
                ArrayNode arr = itemsFuture.get();
                return ResponseEntity.ok(arr);
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION)
                );
                ArrayNode arr = itemsFuture.get();
                return ResponseEntity.ok(arr);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for listProducts", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERRUPTED", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while listing Products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
        }
    }

    private ResponseEntity<Object> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", cause.getMessage()));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Bad request", cause);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("INVALID_ARGUMENT", cause.getMessage()));
        } else {
            logger.error("Execution error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("EXECUTION_ERROR", cause != null ? cause.getMessage() : e.getMessage()));
        }
    }

    private Map<String, Object> errorBody(String code, String message) {
        return Map.of("errorCode", code, "message", message != null ? message : "");
    }

    @Data
    @Schema(name = "ProductResponse")
    public static class ProductResponse {
        private String productId;
        private String technicalId;
        private String name;
        private String category;
        private Double price;
        private Integer stockLevel;
        private Integer reorderPoint;
        private Map<String, Object> metrics;
        private java.util.List<String> flags;
        private String lastUpdated;
        private String createdAt;
    }
}
