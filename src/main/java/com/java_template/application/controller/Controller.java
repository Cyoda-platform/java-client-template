package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.userimportjob.version_1.UserImportJob;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@Tag(name = "Order Management API", description = "Event-driven API for Order Management System")
@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // --- UserImportJob Endpoints ---

    @Operation(summary = "Create UserImportJob to start user import", description = "Create UserImportJob entity with importData to initiate user import workflow")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "UserImportJob created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Entity not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/admin/users/import")
    public ResponseEntity<?> createUserImportJob(@RequestBody UserImportJobCreateRequest request) {
        try {
            UserImportJob userImportJob = new UserImportJob();
            userImportJob.setImportData(request.getImportData());
            // jobId, status, createdAt will be set by workflow or persistence

            CompletableFuture<UUID> idFuture = entityService.addItem(
                UserImportJob.ENTITY_NAME,
                String.valueOf(UserImportJob.ENTITY_VERSION),
                userImportJob
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createUserImportJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createUserImportJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createUserImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Retrieve UserImportJob status and details", description = "Get UserImportJob entity by jobId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "UserImportJob entity", content = @Content(schema = @Schema(implementation = UserImportJob.class))),
        @ApiResponse(responseCode = "400", description = "Invalid jobId"),
        @ApiResponse(responseCode = "404", description = "UserImportJob not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/admin/users/import/{technicalId}")
    public ResponseEntity<?> getUserImportJob(
        @Parameter(name = "technicalId", description = "Technical ID of the UserImportJob")
        @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                UserImportJob.ENTITY_NAME,
                String.valueOf(UserImportJob.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode entity = itemFuture.get();
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getUserImportJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUserImportJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getUserImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- User Endpoints ---

    @Operation(summary = "Retrieve User details", description = "Get User entity by userId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User entity", content = @Content(schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "400", description = "Invalid userId"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/users/{technicalId}")
    public ResponseEntity<?> getUser(
        @Parameter(name = "technicalId", description = "Technical ID of the User")
        @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode entity = itemFuture.get();
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getUser", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUser", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Cart Endpoints ---

    @Operation(summary = "Create or add items to Cart for a customer", description = "Create a new Cart or add items to ACTIVE Cart for customer")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cart created or updated", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Customer or Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/customers/{customerId}/cart")
    public ResponseEntity<?> createOrUpdateCart(
        @Parameter(name = "customerId", description = "Technical ID of the Customer")
        @PathVariable("customerId") String customerId,
        @RequestBody CartCreateRequest request) {
        try {
            Cart cart = new Cart();
            cart.setCustomerId(customerId);
            cart.setItems(request.getItems());
            // cartId, status, createdAt set by workflow or persistence

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                cart
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createOrUpdateCart", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createOrUpdateCart", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createOrUpdateCart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Retrieve current Cart for customer", description = "Get current Cart entity for a customer")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cart entity", content = @Content(schema = @Schema(implementation = Cart.class))),
        @ApiResponse(responseCode = "400", description = "Invalid customerId"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/customers/{customerId}/cart")
    public ResponseEntity<?> getCartForCustomer(
        @Parameter(name = "customerId", description = "Technical ID of the Customer")
        @PathVariable("customerId") String customerId) {
        try {
            // According to requirements, retrieve current Cart for customer,
            // likely by condition on customerId and status ACTIVE
            // Use condition for search
            String condition = "{\"group\":\"AND\",\"conditions\":[{\"field\":\"$.customerId\",\"operator\":\"EQUALS\",\"value\":\"" + customerId + "\"},{\"field\":\"$.status\",\"operator\":\"EQUALS\",\"value\":\"ACTIVE\"}]}";

            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                condition,
                true
            );
            com.fasterxml.jackson.databind.node.ArrayNode carts = itemsFuture.get();
            if (carts.size() == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Active Cart not found for customerId: " + customerId);
            }
            // Return the first active cart found
            return ResponseEntity.ok(carts.get(0));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getCartForCustomer", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getCartForCustomer", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getCartForCustomer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Order Endpoints ---

    @Operation(summary = "Create Order from Cart checkout", description = "Create Order entity from Cart checkout")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Cart or Customer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/customers/{customerId}/orders")
    public ResponseEntity<?> createOrder(
        @Parameter(name = "customerId", description = "Technical ID of the Customer")
        @PathVariable("customerId") String customerId,
        @RequestBody OrderCreateRequest request) {
        try {
            Order order = new Order();
            order.setCustomerId(customerId);
            order.setStatus("PENDING");
            // orderId and createdAt set by workflow or persistence
            // items will be set by workflow after cart checkout event

            // Here just persist the order with customerId and status
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                order
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createOrder", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createOrder", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createOrder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Retrieve Order details", description = "Get Order entity by orderId for a customer")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order entity", content = @Content(schema = @Schema(implementation = Order.class))),
        @ApiResponse(responseCode = "400", description = "Invalid orderId"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/customers/{customerId}/orders/{technicalId}")
    public ResponseEntity<?> getOrder(
        @Parameter(name = "customerId", description = "Technical ID of the Customer")
        @PathVariable("customerId") String customerId,
        @Parameter(name = "technicalId", description = "Technical ID of the Order")
        @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode entity = itemFuture.get();
            // Optionally, could validate that entity.customerId matches customerId, but not in controller as per instructions
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getOrder", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getOrder", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getOrder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Static DTO classes ---

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "job-1234")
        private final String technicalId;
    }

    @Data
    public static class UserImportJobCreateRequest {
        @Schema(description = "Raw user data in JSON or CSV format for import", example = "[{\"name\":\"Alice\", \"email\":\"alice@example.com\", \"role\":\"Admin\"}]")
        private String importData;
    }

    @Data
    public static class CartCreateRequest {
        @Schema(description = "List of items to add to the cart")
        private java.util.List<CartItemDTO> items;
    }

    @Data
    public static class CartItemDTO {
        @Schema(description = "Product identifier", example = "prod-123")
        private String productId;
        @Schema(description = "Quantity of the product", example = "2")
        private Integer quantity;
    }

    @Data
    public static class OrderCreateRequest {
        @Schema(description = "Cart identifier to checkout", example = "cart-789")
        private String cartId;
    }
}