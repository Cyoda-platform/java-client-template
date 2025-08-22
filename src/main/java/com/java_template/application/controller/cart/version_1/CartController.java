package com.java_template.application.controller.cart.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.cart.version_1.Cart;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/api/v1/carts")
@Tag(name = "Cart", description = "Cart entity operations (version 1)")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CartController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Cart", description = "Create a new Cart entity and trigger Cart workflow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CartCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createCart(
            @RequestBody(description = "Cart create payload", required = true,
                    content = @Content(schema = @Schema(implementation = CartRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CartRequest request) {
        try {
            // Basic validation
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Convert request DTO to a JSON object to pass to EntityService (controller must be a simple proxy)
            ObjectNode payload = objectMapper.valueToTree(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    payload
            );

            UUID technicalId = idFuture.get();

            CartCreateResponse response = new CartCreateResponse();
            response.setTechnicalId(technicalId.toString());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createCart: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating cart", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while creating cart", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Cart", description = "Retrieve Cart entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getCart(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode item = itemFuture.get();

            CartResponse response = new CartResponse();
            response.setTechnicalId(technicalId);
            response.setCart(item);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getCart: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving cart", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving cart", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(404).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in execution: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution error", ee);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Static DTO classes for request/response payloads

    @Data
    @Schema(name = "CartRequest", description = "Payload to create a Cart")
    public static class CartRequest {
        @Schema(description = "Business id of the cart", example = "C-789")
        private String id;

        @Schema(description = "Owner user id or null for guest", example = "U-1")
        private String userId;

        @Schema(description = "Cart items")
        private java.util.List<ItemDto> items;

        @Schema(description = "Total amount", example = "0")
        private Double total;

        @Schema(description = "Status of the cart", example = "Active")
        private String status;

        @Schema(description = "Creation timestamp", example = "2023-01-01T12:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "ItemDto", description = "Cart item")
    public static class ItemDto {
        @Schema(description = "Product id", example = "P-123")
        private String productId;

        @Schema(description = "Quantity of the item", example = "2")
        private Integer quantity;

        @Schema(description = "Price at the time of add", example = "19.99")
        private Double priceAtAdd;
    }

    @Data
    @Schema(name = "CartCreateResponse", description = "Response after creating a Cart")
    public static class CartCreateResponse {
        @Schema(description = "Technical id assigned to the entity", example = "tech-cart-xyz456")
        private String technicalId;
    }

    @Data
    @Schema(name = "CartResponse", description = "Cart entity response including technicalId")
    public static class CartResponse {
        @Schema(description = "Technical id of the entity", example = "tech-cart-xyz456")
        private String technicalId;

        @Schema(description = "Cart entity payload")
        private ObjectNode cart;
    }
}