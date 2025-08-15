package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.time.Instant;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
@Tag(name = "Controller", description = "Dull proxy controller for entity services")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    /*
     * Import Users
     */
    @Operation(summary = "Import Users", description = "Create an ImportJob for Users and trigger processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/import/users")
    public ResponseEntity<?> importUsers(@RequestBody ImportRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("request body required");
            if (request.getSource() == null || request.getSource().isBlank()) throw new IllegalArgumentException("source required");
            if (request.getUploadedBy() == null || request.getUploadedBy().isBlank()) throw new IllegalArgumentException("uploadedBy required");

            ImportJob job = new ImportJob();
            job.setId(UUID.randomUUID().toString());
            job.setType("User");
            job.setSource(request.getSource());
            job.setUploadedBy(request.getUploadedBy());
            job.setStatus("Pending");
            job.setFileUrl(request.getFileUrl());
            job.setCreatedAt(Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ImportJob.ENTITY_NAME,
                String.valueOf(ImportJob.ENTITY_VERSION),
                job
            );

            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("ExecutionException during importUsers", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in importUsers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Import Products
     */
    @Operation(summary = "Import Products", description = "Create an ImportJob for Products and trigger processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/import/products")
    public ResponseEntity<?> importProducts(@RequestBody ImportRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("request body required");
            if (request.getSource() == null || request.getSource().isBlank()) throw new IllegalArgumentException("source required");
            if (request.getUploadedBy() == null || request.getUploadedBy().isBlank()) throw new IllegalArgumentException("uploadedBy required");

            ImportJob job = new ImportJob();
            job.setId(UUID.randomUUID().toString());
            job.setType("Product");
            job.setSource(request.getSource());
            job.setUploadedBy(request.getUploadedBy());
            job.setStatus("Pending");
            job.setFileUrl(request.getFileUrl());
            job.setCreatedAt(Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ImportJob.ENTITY_NAME,
                String.valueOf(ImportJob.ENTITY_VERSION),
                job
            );

            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("ExecutionException during importProducts", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in importProducts", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Get ImportJob by technicalId
     */
    @Operation(summary = "Get ImportJob", description = "Retrieve an ImportJob by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJob.class))),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/imports/{technicalId}")
    public ResponseEntity<?> getImportJob(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ImportJob.ENTITY_NAME,
                String.valueOf(ImportJob.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in getImportJob", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getImportJob", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Create ShoppingCart for customer
     */
    @Operation(summary = "Create ShoppingCart", description = "Create a ShoppingCart for a customer and trigger recalculation workflows")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @PostMapping("/customers/{customerId}/carts")
    public ResponseEntity<?> createCart(
        @Parameter(name = "customerId", description = "Customer ID") @PathVariable String customerId,
        @RequestBody CartRequest request
    ) {
        try {
            if (request == null) throw new IllegalArgumentException("request body required");
            if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("customerId required");

            ShoppingCart cart = new ShoppingCart();
            cart.setId(UUID.randomUUID().toString());
            cart.setCustomerId(customerId);
            List<ShoppingCart.Item> items = new ArrayList<>();
            if (request.getItems() != null) {
                for (CartItemDto it : request.getItems()) {
                    ShoppingCart.Item ent = new ShoppingCart.Item();
                    ent.setProductId(it.getProductId());
                    ent.setSku(it.getSku());
                    ent.setQuantity(it.getQuantity());
                    ent.setUnitPrice(it.getUnitPrice());
                    items.add(ent);
                }
            }
            cart.setItems(items);
            cart.setStatus("Open");
            cart.setCreatedAt(Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ShoppingCart.ENTITY_NAME,
                String.valueOf(ShoppingCart.ENTITY_VERSION),
                cart
            );

            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in createCart", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createCart", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Get ShoppingCart by technicalId
     */
    @Operation(summary = "Get ShoppingCart", description = "Retrieve a ShoppingCart by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ShoppingCart.class))),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/carts/{technicalId}")
    public ResponseEntity<?> getCart(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ShoppingCart.ENTITY_NAME,
                String.valueOf(ShoppingCart.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in getCart", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getCart", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Checkout - create Order event
     */
    @Operation(summary = "Checkout Cart", description = "Checkout a cart and create an Order (event-driven)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @PostMapping("/customers/{customerId}/carts/{cartId}/checkout")
    public ResponseEntity<?> checkout(
        @Parameter(name = "customerId", description = "Customer ID") @PathVariable String customerId,
        @Parameter(name = "cartId", description = "Cart ID") @PathVariable String cartId,
        @RequestBody CheckoutRequest request
    ) {
        try {
            if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("customerId required");
            if (cartId == null || cartId.isBlank()) throw new IllegalArgumentException("cartId required");

            Order order = new Order();
            order.setId(UUID.randomUUID().toString());
            order.setOrderNumber("ORD-" + UUID.randomUUID().toString());
            order.setCustomerId(customerId);
            order.setItems(new ArrayList<>());
            order.setSubtotal(BigDecimal.ZERO);
            order.setTaxes(BigDecimal.ZERO);
            order.setShipping(BigDecimal.ZERO);
            order.setTotal(BigDecimal.ZERO);
            order.setCurrency(request != null && request.getCurrency() != null ? request.getCurrency() : "USD");
            order.setPaymentStatus("Pending");
            order.setFulfillmentStatus("Pending");
            order.setCreatedAt(Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                order
            );

            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in checkout", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in checkout", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Get Order by technicalId
     */
    @Operation(summary = "Get Order", description = "Retrieve an Order by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Order.class))),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/orders/{technicalId}")
    public ResponseEntity<?> getOrder(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in getOrder", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getOrder", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Get all Products
     */
    @Operation(summary = "Get Products", description = "Retrieve all products")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Product.class))))
    })
    @GetMapping("/products")
    public ResponseEntity<?> getProducts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in getProducts", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getProducts", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Get Product by productId
     */
    @Operation(summary = "Get Product", description = "Retrieve a Product by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Product.class))),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getProduct(@Parameter(name = "productId", description = "Product ID") @PathVariable String productId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                UUID.fromString(productId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in getProduct", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getProduct", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Get User by id (admin)
     */
    @Operation(summary = "Get User", description = "Admin: retrieve a User by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/admin/users/{userId}")
    public ResponseEntity<?> getUser(@Parameter(name = "userId", description = "User ID") @PathVariable String userId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(userId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(404).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("ExecutionException in getUser", ee);
            return ResponseEntity.status(500).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getUser", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Static DTOs

    @Data
    @Schema(description = "Response containing a technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the saved entity")
        private String technicalId;

        public TechnicalIdResponse() {}
        public TechnicalIdResponse(String technicalId) { this.technicalId = technicalId; }
    }

    @Data
    @Schema(description = "Import request payload")
    public static class ImportRequest {
        @Schema(description = "Source of import: CSV|JSON|API")
        private String source;
        @Schema(description = "Uploaded by user id")
        private String uploadedBy;
        @Schema(description = "Optional file URL")
        private String fileUrl;
        @Schema(description = "Optional inlined payload / records")
        private Map<String,Object> payload = new HashMap<>();
    }

    @Data
    @Schema(description = "Cart creation request")
    public static class CartRequest {
        @Schema(description = "Customer ID")
        private String customerId;
        @Schema(description = "Line items")
        private List<CartItemDto> items = new ArrayList<>();
    }

    @Data
    @Schema(description = "Cart item DTO")
    public static class CartItemDto {
        @Schema(description = "Product ID")
        private String productId;
        @Schema(description = "SKU")
        private String sku;
        @Schema(description = "Quantity")
        private Integer quantity;
        @Schema(description = "Unit price")
        private BigDecimal unitPrice;
    }

    @Data
    @Schema(description = "Checkout request payload")
    public static class CheckoutRequest {
        @Schema(description = "Customer ID")
        private String customerId;
        @Schema(description = "Cart ID")
        private String cartId;
        @Schema(description = "Payment method (card|stub)")
        private Map<String,Object> payment = new HashMap<>();
        @Schema(description = "Shipping information")
        private Map<String,Object> shipping = new HashMap<>();
        @Schema(description = "Currency")
        private String currency;
    }

}
