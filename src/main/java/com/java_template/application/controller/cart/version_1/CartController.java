package com.java_template.application.controller.cart.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.cart.version_1.Cart;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
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
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/ui")
@Tag(name = "Cart UI Controller", description = "Proxy controller for Cart entity (version 1)")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CartController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create or return cart", description = "Create a new cart (or return existing). Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/cart")
    public ResponseEntity<?> createCart(@RequestBody CreateCartRequest request) {
        try {
            if (request == null || request.action == null || request.action.isBlank()) {
                throw new IllegalArgumentException("action is required");
            }
            // Basic validation only. Business logic handled in workflows.
            if (!"createOrReturn".equals(request.action)) {
                throw new IllegalArgumentException("unsupported action");
            }

            Cart cart = new Cart();
            String now = Instant.now().toString();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            cart.setGrandTotal(0.0);
            cart.setTotalItems(0);
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            // Persist as entity event
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Cart.ENTITY_NAME,
                    Cart.ENTITY_VERSION,
                    cart
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = entityId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createCart: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createCart", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Add a line to cart", description = "Adds a line to the specified cart. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/cart/{technicalId}/lines")
    public ResponseEntity<?> addLine(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody AddLineRequest request) {
        try {
            if (request == null || request.sku == null || request.sku.isBlank()) {
                throw new IllegalArgumentException("sku is required");
            }
            if (request.qty == null || request.qty <= 0) {
                throw new IllegalArgumentException("qty must be > 0");
            }

            // Build minimal valid Cart entity representing the line add event
            Cart cart = new Cart();
            String now = Instant.now().toString();
            cart.setCartId(technicalId);
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            cart.setGrandTotal(0.0);
            cart.setStatus("ACTIVE");
            cart.setTotalItems(request.qty);

            Cart.Line line = new Cart.Line();
            line.setSku(request.sku);
            line.setQty(request.qty);
            line.setPrice(0.0); // no price provided in request; workflows handle pricing
            line.setName(request.sku); // minimal placeholder

            List<Cart.Line> lines = new ArrayList<>();
            lines.add(line);
            cart.setLines(lines);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Cart.ENTITY_NAME,
                    Cart.ENTITY_VERSION,
                    cart
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = entityId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addLine: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addLine", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in addLine", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addLine", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a line in cart", description = "Update line quantity in the specified cart. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PatchMapping("/cart/{technicalId}/lines")
    public ResponseEntity<?> updateLine(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody AddLineRequest request) {
        try {
            if (request == null || request.sku == null || request.sku.isBlank()) {
                throw new IllegalArgumentException("sku is required");
            }
            if (request.qty == null || request.qty <= 0) {
                throw new IllegalArgumentException("qty must be > 0");
            }

            // Build minimal Cart entity representing the line update event
            Cart cart = new Cart();
            String now = Instant.now().toString();
            cart.setCartId(technicalId);
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            cart.setGrandTotal(0.0);
            cart.setStatus("ACTIVE");
            cart.setTotalItems(request.qty);

            Cart.Line line = new Cart.Line();
            line.setSku(request.sku);
            line.setQty(request.qty);
            line.setPrice(0.0);
            line.setName(request.sku);

            List<Cart.Line> lines = new ArrayList<>();
            lines.add(line);
            cart.setLines(lines);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Cart.ENTITY_NAME,
                    Cart.ENTITY_VERSION,
                    cart
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = entityId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateLine: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateLine", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updateLine", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateLine", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Open checkout for cart", description = "Mark cart as open for checkout. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/cart/{technicalId}/open-checkout")
    public ResponseEntity<?> openCheckout(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            Cart cart = new Cart();
            String now = Instant.now().toString();
            cart.setCartId(technicalId);
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            cart.setGrandTotal(0.0);
            cart.setStatus("CHECKING_OUT");
            cart.setTotalItems(0);
            cart.setLines(new ArrayList<>());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Cart.ENTITY_NAME,
                    Cart.ENTITY_VERSION,
                    cart
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = entityId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for openCheckout: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in openCheckout", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in openCheckout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in openCheckout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Checkout cart with guest contact", description = "Submit guest contact and checkout the cart. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/checkout/{technicalId}")
    public ResponseEntity<?> checkout(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody CheckoutRequest request) {
        try {
            if (request == null || request.guestContact == null) {
                throw new IllegalArgumentException("guestContact is required");
            }
            // Map request to Cart.GuestContact
            Cart.GuestContact guest = new Cart.GuestContact();
            guest.setName(request.guestContact.name);
            guest.setEmail(request.guestContact.email);
            guest.setPhone(request.guestContact.phone);

            Cart.Address addr = new Cart.Address();
            if (request.guestContact.address != null) {
                addr.setLine1(request.guestContact.address.line1);
                addr.setCity(request.guestContact.address.city);
                addr.setPostcode(request.guestContact.address.postcode);
                addr.setCountry(request.guestContact.address.country);
            }
            guest.setAddress(addr);

            Cart cart = new Cart();
            String now = Instant.now().toString();
            cart.setCartId(technicalId);
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            cart.setGrandTotal(0.0);
            cart.setStatus("CHECKING_OUT");
            cart.setTotalItems(0);
            cart.setLines(new ArrayList<>());
            cart.setGuestContact(guest);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Cart.ENTITY_NAME,
                    Cart.ENTITY_VERSION,
                    cart
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.technicalId = entityId.toString();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for checkout: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in checkout", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in checkout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in checkout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get cart by technicalId", description = "Retrieve stored cart by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/cart/{technicalId}")
    public ResponseEntity<?> getCart(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Cart not found");
            }
            CartResponse resp = objectMapper.treeToValue(node, CartResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getCart: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getCart", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateCartRequest", description = "Request to create or return cart")
    public static class CreateCartRequest {
        @Schema(description = "Action to perform", example = "createOrReturn")
        public String action;
    }

    @Data
    @Schema(name = "AddLineRequest", description = "Request to add or update a cart line")
    public static class AddLineRequest {
        @Schema(description = "SKU of the product", example = "SKU-1")
        public String sku;
        @Schema(description = "Quantity", example = "2")
        public Integer qty;
    }

    @Data
    @Schema(name = "CheckoutRequest", description = "Request to checkout a cart with guest contact")
    public static class CheckoutRequest {
        @Schema(description = "Guest contact information")
        public GuestContactRequest guestContact;

        @Data
        @Schema(name = "GuestContactRequest", description = "Guest contact details")
        public static class GuestContactRequest {
            @Schema(description = "Name", example = "Jane")
            public String name;
            @Schema(description = "Email", example = "j@x.com")
            public String email;
            @Schema(description = "Phone", example = "+4412345")
            public String phone;
            @Schema(description = "Address")
            public AddressRequest address;
        }

        @Data
        @Schema(name = "AddressRequest", description = "Address details")
        public static class AddressRequest {
            @Schema(description = "Line1", example = "1 St")
            public String line1;
            @Schema(description = "City", example = "City")
            public String city;
            @Schema(description = "Postcode", example = "PC1")
            public String postcode;
            @Schema(description = "Country", example = "GB")
            public String country;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the persisted entity", example = "t-cart-123")
        public String technicalId;
    }

    @Data
    @Schema(name = "CartResponse", description = "Cart entity response")
    public static class CartResponse {
        @Schema(description = "Business cart id")
        public String cartId;
        @Schema(description = "Status", example = "ACTIVE")
        public String status;
        @Schema(description = "Lines")
        public List<LineResponse> lines;
        @Schema(description = "Total items")
        public Integer totalItems;
        @Schema(description = "Grand total")
        public Double grandTotal;
        @Schema(description = "Guest contact")
        public GuestContactResponse guestContact;
        @Schema(description = "Created at")
        public String createdAt;
        @Schema(description = "Updated at")
        public String updatedAt;

        @Data
        @Schema(name = "LineResponse", description = "Cart line")
        public static class LineResponse {
            @Schema(description = "Name")
            public String name;
            @Schema(description = "Price")
            public Double price;
            @Schema(description = "Quantity")
            public Integer qty;
            @Schema(description = "SKU")
            public String sku;
        }

        @Data
        @Schema(name = "GuestContactResponse", description = "Guest contact details")
        public static class GuestContactResponse {
            @Schema(description = "Address")
            public AddressResponse address;
            @Schema(description = "Email")
            public String email;
            @Schema(description = "Name")
            public String name;
            @Schema(description = "Phone")
            public String phone;
        }

        @Data
        @Schema(name = "AddressResponse", description = "Address details")
        public static class AddressResponse {
            @Schema(description = "City")
            public String city;
            @Schema(description = "Country")
            public String country;
            @Schema(description = "Line1")
            public String line1;
            @Schema(description = "Postcode")
            public String postcode;
        }
    }
}