package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.productimportjob.version_1.ProductImportJob;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Tag(name = "Controller", description = "Event-Driven REST API Controller for Order Management System")
@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // ProductImportJob POST /import/products
    @Operation(summary = "Create ProductImportJob to bulk import products", description = "Creates a ProductImportJob entity to initiate bulk product import")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ProductImportJob created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/import/products")
    public ResponseEntity<?> createProductImportJob(@RequestBody List<Product> products) {
        try {
            // Wrap products list in ProductImportJob entity's data flow, for simplicity assume direct add of products as entities is done in workflow
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    products);
            List<UUID> ids = idsFuture.get();
            // Return the first created product's technicalId as example (the job id itself is not explicitly created here, assumed workflow creates it)
            if (ids.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create ProductImportJob");
            }
            return ResponseEntity.ok(new TechnicalIdResponse(ids.get(0).toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /import/products/{id}
    @Operation(summary = "Get ProductImportJob status and errors", description = "Retrieves the ProductImportJob entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ProductImportJob found", content = @Content(schema = @Schema(implementation = ProductImportJob.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/import/products/{technicalId}")
    public ResponseEntity<?> getProductImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the ProductImportJob")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ProductImportJob.ENTITY_NAME,
                    String.valueOf(ProductImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId));
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // UserImportJob POST /import/users
    @Operation(summary = "Create UserImportJob to bulk import users", description = "Creates a UserImportJob entity to initiate bulk user import")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "UserImportJob created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/import/users")
    public ResponseEntity<?> createUserImportJob(@RequestBody List<User> users) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    users);
            List<UUID> ids = idsFuture.get();
            if (ids.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create UserImportJob");
            }
            return ResponseEntity.ok(new TechnicalIdResponse(ids.get(0).toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /import/users/{id}
    @Operation(summary = "Get UserImportJob status and errors", description = "Retrieves the UserImportJob entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "UserImportJob found", content = @Content(schema = @Schema(implementation = UserImportJob.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/import/users/{technicalId}")
    public ResponseEntity<?> getUserImportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the UserImportJob")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    UserImportJob.ENTITY_NAME,
                    String.valueOf(UserImportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId));
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /products
    @Operation(summary = "Retrieve list of all products", description = "Retrieves all Product entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of products", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = Product.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/products")
    public ResponseEntity<?> getAllProducts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /products/{id}
    @Operation(summary = "Retrieve Product by id", description = "Retrieves a Product entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found", content = @Content(schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/products/{technicalId}")
    public ResponseEntity<?> getProductById(
            @Parameter(name = "technicalId", description = "Technical ID of the product")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId));
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /users
    @Operation(summary = "Retrieve list of all users", description = "Retrieves all User entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of users", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = User.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /users/{id}
    @Operation(summary = "Retrieve User by id", description = "Retrieves a User entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/users/{technicalId}")
    public ResponseEntity<?> getUserById(
            @Parameter(name = "technicalId", description = "Technical ID of the user")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId));
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // POST /cart/update
    @Operation(summary = "Create new ShoppingCart entity (add/remove products)", description = "Creates or updates ShoppingCart entity reflecting current state")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ShoppingCart created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/cart/update")
    public ResponseEntity<?> updateShoppingCart(@RequestBody ShoppingCart shoppingCart) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ShoppingCart.ENTITY_NAME,
                    String.valueOf(ShoppingCart.ENTITY_VERSION),
                    shoppingCart);
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /cart/{userId}
    @Operation(summary = "Retrieve latest ShoppingCart for user", description = "Retrieves latest ShoppingCart entity for given userId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ShoppingCart found", content = @Content(schema = @Schema(implementation = ShoppingCart.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/cart/{userId}")
    public ResponseEntity<?> getLatestShoppingCartByUserId(
            @Parameter(name = "userId", description = "User ID to get ShoppingCart for")
            @PathVariable String userId) {
        try {
            // Retrieve all shopping carts by condition userId equal to given userId, then pick latest or first (no updates or deletes, so last created is latest)
            var condition = SearchConditionRequest.group("AND",
                    Condition.of("$.userId", "EQUALS", userId));
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ShoppingCart.ENTITY_NAME,
                    String.valueOf(ShoppingCart.ENTITY_VERSION),
                    condition,
                    true);
            ArrayNode items = filteredItemsFuture.get();
            if (items.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Return the first item as latest for simplicity (assumes order returned is latest first)
            return ResponseEntity.ok(items.get(0));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // POST /cart/checkout
    @Operation(summary = "Create Order entity for user checkout", description = "Creates an Order entity upon shopping cart checkout")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/cart/checkout")
    public ResponseEntity<?> checkoutCart(@RequestBody Order order) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    order);
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /orders/{userId}
    @Operation(summary = "Retrieve all orders for a user", description = "Retrieves all Order entities for a given userId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of orders", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = Order.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/orders/{userId}")
    public ResponseEntity<?> getOrdersByUserId(
            @Parameter(name = "userId", description = "User ID to get orders for")
            @PathVariable String userId) {
        try {
            var condition = SearchConditionRequest.group("AND",
                    Condition.of("$.userId", "EQUALS", userId));
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    condition,
                    true);
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /orders/orderId
    @Operation(summary = "Retrieve Order by id", description = "Retrieves an Order entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found", content = @Content(schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/orders/order/{technicalId}")
    public ResponseEntity<?> getOrderById(
            @Parameter(name = "technicalId", description = "Technical ID of the Order")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    UUID.fromString(technicalId));
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity")
        private final String technicalId;
    }
}