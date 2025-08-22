package com.java_template.application.controller.cart.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.cart.version_1.Cart;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for Cart entity. All business logic is implemented in workflows/processors.
 */
@RestController
@RequestMapping(path = "/api/v1/carts", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Cart API", description = "Proxy endpoints for Cart entity (version 1)")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CartController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Cart", description = "Persist a new Cart entity. Returns only technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> createCart(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Cart creation payload", required = true,
        content = @Content(schema = @Schema(implementation = CreateCartRequest.class)))
                                                      @RequestBody CreateCartRequest request) {
        try {
            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                data
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createCart: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in createCart", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Bulk create Carts", description = "Persist multiple Cart entities. Returns list of technicalIds.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdsResponse> createCartsBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk carts payload", required = true,
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateCartRequest.class))))
                                                           @RequestBody List<CreateCartRequest> requests) {
        try {
            ArrayNode data = objectMapper.valueToTree(requests);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                data
            );
            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createCartsBulk: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in createCartsBulk", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating carts bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createCartsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Cart by technicalId", description = "Retrieve a Cart by its technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetCartResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL)
    public ResponseEntity<GetCartResponse> getCartByTechnicalId(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode entity = itemFuture.get();
            return ResponseEntity.ok(new GetCartResponse(technicalId, entity));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getCartByTechnicalId: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in getCartByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getCartByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get all Carts", description = "Retrieve all Cart entities.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Object.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL)
    public ResponseEntity<ArrayNode> getAllCarts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in getAllCarts", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching all carts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getAllCarts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search Carts by condition", description = "Retrieve Cart entities matching the provided search condition.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Object.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ArrayNode> searchCartsByCondition(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
        @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition for searchCartsByCondition: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in searchCartsByCondition", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching carts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in searchCartsByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update Cart", description = "Update an existing Cart entity by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> updateCart(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Cart update payload", required = true,
            content = @Content(schema = @Schema(implementation = CreateCartRequest.class)))
        @RequestBody CreateCartRequest request) {
        try {
            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                UUID.fromString(technicalId),
                data
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateCart: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in updateCart", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in updateCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete Cart", description = "Delete a Cart entity by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", consumes = MediaType.ALL)
    public ResponseEntity<TechnicalIdResponse> deleteCart(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteCart: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in deleteCart", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in deleteCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static DTO classes for requests/responses

    @Data
    public static class CreateCartRequest {
        @Schema(description = "Business identifier of the cart", example = "cart-1")
        private String id;

        @Schema(description = "User id (nullable for ANON)", example = "user-1")
        private String userId;

        @Schema(description = "Cart status", example = "NEW")
        private String status;

        @Schema(description = "Items in the cart")
        private List<CartItemDto> items;

        @Schema(description = "Total amount of the cart", example = "0.0")
        private Double totalAmount;

        @Schema(description = "Creation timestamp (ISO8601)", example = "2025-01-01T00:00:00Z")
        private String createdAt;

        @Schema(description = "Update timestamp (ISO8601)", example = "2025-01-01T00:00:00Z")
        private String updatedAt;
    }

    @Data
    public static class CartItemDto {
        @Schema(description = "Product business id", example = "sku-123")
        private String productId;

        @Schema(description = "Quantity of the product", example = "1")
        private Integer quantity;

        @Schema(description = "Unit price for the item", example = "19.99")
        private Double unitPrice;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "tx-cart-001")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    public static class GetCartResponse {
        @Schema(description = "Technical id of the cart", example = "tx-cart-001")
        private String technicalId;

        @Schema(description = "Cart entity as stored")
        private ObjectNode entity;

        public GetCartResponse(String technicalId, ObjectNode entity) {
            this.technicalId = technicalId;
            this.entity = entity;
        }
    }
}