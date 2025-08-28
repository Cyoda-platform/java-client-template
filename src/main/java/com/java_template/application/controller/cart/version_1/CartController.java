package com.java_template.application.controller.cart.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.service.EntityService;
import lombok.Data;
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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

/**
 * Dull proxy controller for Cart entity.
 * All business logic is implemented in workflows; controller only proxies to EntityService.
 */
@RestController
@RequestMapping("/entity/cart")
@Tag(name = "Cart", description = "CRUD endpoints for Cart entity (proxy to EntityService)")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CartController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Cart", description = "Persist a new Cart entity. Returns only technicalId. Business logic is handled by workflows.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createCart(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Cart create payload", content = @Content(schema = @Schema(implementation = CreateCartRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateCartRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Map request to entity
            Cart cart = new Cart();
            // Ensure required entity fields are set to satisfy entity validation
            cart.setId(request.getId() != null && !request.getId().isBlank() ? request.getId() : UUID.randomUUID().toString());
            cart.setUserId(request.getUserId());
            cart.setStatus(request.getStatus() != null ? request.getStatus() : "OPEN");
            cart.setLastUpdated(request.getLastUpdated() != null ? request.getLastUpdated() : Instant.now().toString());

            List<Cart.CartItem> items = new ArrayList<>();
            if (request.getItems() != null) {
                for (CreateCartRequest.ItemDto it : request.getItems()) {
                    Cart.CartItem ci = new Cart.CartItem();
                    ci.setProductId(it.getProductId());
                    ci.setQty(it.getQty());
                    ci.setPriceSnapshot(it.getPriceSnapshot());
                    items.add(ci);
                }
            }
            cart.setItems(items);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Cart.ENTITY_NAME,
                    Cart.ENTITY_VERSION,
                    cart
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createCart: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createCart", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception during createCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Cart by technicalId", description = "Retrieve a Cart by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getCartById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Cart not found");
            }
            JsonNode dataNode = dataPayload.getData();
            CartResponse response = objectMapper.treeToValue(dataNode, CartResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getCartById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getCartById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception during getCartById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all Carts", description = "Retrieve all Cart entities (pageable not implemented; proxy only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CartResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listCarts() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Cart.ENTITY_NAME,
                    Cart.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<CartResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        CartResponse r = objectMapper.treeToValue(payload.getData(), CartResponse.class);
                        responses.add(r);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during listCarts", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception during listCarts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Cart", description = "Update an existing Cart by technicalId. Returns technicalId of updated event.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateCart(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Cart update payload", content = @Content(schema = @Schema(implementation = UpdateCartRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateCartRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Cart cart = new Cart();
            // Preserve or assign a natural id; avoid business logic here—ensure entity is valid
            cart.setId(request.getId() != null && !request.getId().isBlank() ? request.getId() : technicalId);
            cart.setUserId(request.getUserId());
            cart.setStatus(request.getStatus() != null ? request.getStatus() : "OPEN");
            cart.setLastUpdated(request.getLastUpdated() != null ? request.getLastUpdated() : Instant.now().toString());

            List<Cart.CartItem> items = new ArrayList<>();
            if (request.getItems() != null) {
                for (UpdateCartRequest.ItemDto it : request.getItems()) {
                    Cart.CartItem ci = new Cart.CartItem();
                    ci.setProductId(it.getProductId());
                    ci.setQty(it.getQty());
                    ci.setPriceSnapshot(it.getPriceSnapshot());
                    items.add(ci);
                }
            }
            cart.setItems(items);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    UUID.fromString(technicalId),
                    cart
            );
            UUID updatedTechnicalId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedTechnicalId.toString()));

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateCart: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updateCart", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception during updateCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Cart", description = "Delete a Cart by technicalId. Returns technicalId of deletion event.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteCart(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedTechnicalId = deletedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedTechnicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteCart: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deleteCart", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception during deleteCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateCartRequest", description = "Payload to create a Cart")
    public static class CreateCartRequest {
        @Schema(description = "Natural id of the cart (optional)", example = "cart-1")
        private String id;
        @Schema(description = "User id owning the cart", example = "u-123")
        private String userId;
        @Schema(description = "Cart items")
        private List<ItemDto> items;
        @Schema(description = "Cart status (OPEN, CHECKED_OUT, RELEASED, CANCELLED)", example = "OPEN")
        private String status;
        @Schema(description = "Last updated timestamp (ISO-8601)", example = "2025-08-28T12:00:00Z")
        private String lastUpdated;

        @Data
        @Schema(name = "CreateCartItem", description = "Item in create cart request")
        public static class ItemDto {
            @Schema(description = "Product id", example = "p-1")
            private String productId;
            @Schema(description = "Quantity", example = "2")
            private Integer qty;
            @Schema(description = "Price snapshot", example = "9.99")
            private Double priceSnapshot;
        }
    }

    @Data
    @Schema(name = "UpdateCartRequest", description = "Payload to update a Cart")
    public static class UpdateCartRequest {
        @Schema(description = "Natural id of the cart (optional)", example = "cart-1")
        private String id;
        @Schema(description = "User id owning the cart", example = "u-123")
        private String userId;
        @Schema(description = "Cart items")
        private List<ItemDto> items;
        @Schema(description = "Cart status (OPEN, CHECKED_OUT, RELEASED, CANCELLED)", example = "OPEN")
        private String status;
        @Schema(description = "Last updated timestamp (ISO-8601)", example = "2025-08-28T12:00:00Z")
        private String lastUpdated;

        @Data
        @Schema(name = "UpdateCartItem", description = "Item in update cart request")
        public static class ItemDto {
            @Schema(description = "Product id", example = "p-1")
            private String productId;
            @Schema(description = "Quantity", example = "2")
            private Integer qty;
            @Schema(description = "Price snapshot", example = "9.99")
            private Double priceSnapshot;
        }
    }

    @Data
    @Schema(name = "CartResponse", description = "Cart entity response")
    public static class CartResponse {
        @Schema(description = "Natural id of the cart", example = "cart-1")
        private String id;
        @Schema(description = "User id owning the cart", example = "u-123")
        private String userId;
        @Schema(description = "Cart items")
        private List<CartItemDto> items;
        @Schema(description = "Cart status (OPEN, CHECKED_OUT, RELEASED, CANCELLED)", example = "OPEN")
        private String status;
        @Schema(description = "Last updated timestamp (ISO-8601)", example = "2025-08-28T12:00:00Z")
        private String lastUpdated;

        @Data
        @Schema(name = "CartItem", description = "Item in cart response")
        public static class CartItemDto {
            @Schema(description = "Price snapshot", example = "9.99")
            private Double priceSnapshot;
            @Schema(description = "Product id", example = "p-1")
            private String productId;
            @Schema(description = "Quantity", example = "2")
            private Integer qty;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id assigned to the persisted event", example = "c4e3b2a1-...")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}