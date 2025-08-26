package com.java_template.application.controller.shoppingcart.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/shopping-carts")
@Tag(name = "ShoppingCart", description = "ShoppingCart entity proxy endpoints (version 1)")
public class ShoppingCartController {

    private static final Logger logger = LoggerFactory.getLogger(ShoppingCartController.class);

    private final EntityService entityService;

    public ShoppingCartController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ShoppingCart", description = "Create a new ShoppingCart. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createShoppingCart(
            @RequestBody(description = "ShoppingCart create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateShoppingCartRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateShoppingCartRequest request) {
        try {
            if (request == null || request.getCustomerUserId() == null || request.getCustomerUserId().isBlank()) {
                throw new IllegalArgumentException("customerUserId is required");
            }

            ShoppingCart cart = new ShoppingCart();
            // Minimal population to satisfy entity structure; workflows handle business rules.
            cart.setCartId(UUID.randomUUID().toString());
            cart.setCustomerUserId(request.getCustomerUserId());
            cart.setCreatedAt(Instant.now().toString());
            cart.setModifiedAt(null);
            cart.setItems(new ArrayList<>());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ShoppingCart.ENTITY_NAME,
                    String.valueOf(ShoppingCart.ENTITY_VERSION),
                    cart
            );

            java.util.UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for createShoppingCart", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in createShoppingCart", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ShoppingCart", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createShoppingCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ShoppingCart by technicalId", description = "Retrieve a ShoppingCart by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ShoppingCart.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getShoppingCart(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ShoppingCart.ENTITY_NAME,
                    String.valueOf(ShoppingCart.ENTITY_VERSION),
                    tid
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for getShoppingCart", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getShoppingCart", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving ShoppingCart", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getShoppingCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all ShoppingCarts", description = "Retrieve all ShoppingCart entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShoppingCart.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listShoppingCarts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ShoppingCart.ENTITY_NAME,
                    String.valueOf(ShoppingCart.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in listShoppingCarts", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing ShoppingCarts", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in listShoppingCarts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search ShoppingCarts by field", description = "Search ShoppingCarts using simple field condition (e.g., customerUserId equals value)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShoppingCart.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchShoppingCarts(
            @RequestBody(description = "Search request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody SearchRequest request) {
        try {
            if (request == null || request.getField() == null || request.getField().isBlank() ||
                    request.getOperator() == null || request.getOperator().isBlank() ||
                    request.getValue() == null) {
                throw new IllegalArgumentException("field, operator and value are required for search");
            }

            SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.%s".formatted(request.getField()), request.getOperator(), request.getValue())
            );

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ShoppingCart.ENTITY_NAME,
                    String.valueOf(ShoppingCart.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid search request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in searchShoppingCarts", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching ShoppingCarts", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchShoppingCarts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete ShoppingCart by technicalId", description = "Delete a ShoppingCart by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteShoppingCart(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<java.util.UUID> deletedIdFuture = entityService.deleteItem(
                    ShoppingCart.ENTITY_NAME,
                    String.valueOf(ShoppingCart.ENTITY_VERSION),
                    tid
            );

            UUID deleted = deletedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deleted.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for deleteShoppingCart", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in deleteShoppingCart", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting ShoppingCart", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteShoppingCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for requests/responses

    @Data
    @Schema(name = "CreateShoppingCartRequest", description = "Request to create a shopping cart")
    public static class CreateShoppingCartRequest {
        @Schema(description = "Customer business id / reference", example = "u123")
        private String customerUserId;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID as string)", example = "a1b2c3d4-...")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "SearchRequest", description = "Simple search request for ShoppingCarts")
    public static class SearchRequest {
        @Schema(description = "Field name in the entity to search (e.g., customerUserId)", example = "customerUserId")
        private String field;

        @Schema(description = "Operator to use (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", example = "EQUALS")
        private String operator;

        @Schema(description = "Value to compare", example = "u123")
        private String value;
    }
}