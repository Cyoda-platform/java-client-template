package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CartController - Shopping Cart API
 * Endpoints: POST /ui/cart, GET /ui/cart/{cartId}, POST /ui/cart/{cartId}/lines, etc.
 */
@RestController
@RequestMapping("/ui/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CartController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create or return existing cart
     * POST /ui/cart
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Cart>> createOrGetCart() {
        try {
            // For demo: create new cart with ULID-like ID
            Cart cart = new Cart();
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cart.setStatus("NEW");
            cart.setLines(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.create(cart);
            logger.info("Cart created with ID: {}", response.entity().getCartId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to create cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get cart by ID
     * GET /ui/cart/{cartId}
     */
    @GetMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> getCart(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            SimpleCondition cartIdCondition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(cartId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cartIdCondition));

            List<EntityWithMetadata<Cart>> results = entityService.search(
                    modelSpec, condition, Cart.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(results.get(0));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Add item to cart
     * POST /ui/cart/{cartId}/lines
     */
    @PostMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> addItemToCart(
            @PathVariable String cartId,
            @RequestBody CartLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            SimpleCondition cartIdCondition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(cartId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cartIdCondition));

            List<EntityWithMetadata<Cart>> results = entityService.search(
                    modelSpec, condition, Cart.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Cart> cartWithMetadata = results.get(0);
            Cart cart = cartWithMetadata.entity();

            // Add or update line
            if (cart.getLines() == null) {
                cart.setLines(new ArrayList<>());
            }

            boolean found = false;
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getSku().equals(request.getSku())) {
                    line.setQty(line.getQty() + request.getQty());
                    found = true;
                    break;
                }
            }

            if (!found) {
                Cart.CartLine newLine = new Cart.CartLine();
                newLine.setSku(request.getSku());
                newLine.setName(request.getName());
                newLine.setPrice(request.getPrice());
                newLine.setQty(request.getQty());
                newLine.setLineTotal(request.getPrice() * request.getQty());
                cart.getLines().add(newLine);
            }

            cart.setUpdatedAt(LocalDateTime.now());

            // Update with transition to trigger RecalculateTotalsProcessor
            String transition = cart.getStatus().equals("NEW") ? "CREATE_ON_FIRST_ADD" : "ADD_ITEM";
            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, transition);
            
            logger.info("Item added to cart {}: {}", cartId, request.getSku());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to add item to cart: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update cart line quantity
     * PATCH /ui/cart/{cartId}/lines
     */
    @PatchMapping("/{cartId}/lines")
    public ResponseEntity<EntityWithMetadata<Cart>> updateCartLine(
            @PathVariable String cartId,
            @RequestBody CartLineRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            SimpleCondition cartIdCondition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(cartId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cartIdCondition));

            List<EntityWithMetadata<Cart>> results = entityService.search(
                    modelSpec, condition, Cart.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Cart> cartWithMetadata = results.get(0);
            Cart cart = cartWithMetadata.entity();

            // Update or remove line
            if (cart.getLines() != null) {
                if (request.getQty() == 0) {
                    cart.getLines().removeIf(line -> line.getSku().equals(request.getSku()));
                } else {
                    for (Cart.CartLine line : cart.getLines()) {
                        if (line.getSku().equals(request.getSku())) {
                            line.setQty(request.getQty());
                            line.setLineTotal(request.getPrice() * request.getQty());
                            break;
                        }
                    }
                }
            }

            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, "DECREMENT_ITEM");
            
            logger.info("Cart line updated for {}: {}", cartId, request.getSku());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to update cart line: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Open checkout
     * POST /ui/cart/{cartId}/open-checkout
     */
    @PostMapping("/{cartId}/open-checkout")
    public ResponseEntity<EntityWithMetadata<Cart>> openCheckout(@PathVariable String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            SimpleCondition cartIdCondition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(cartId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cartIdCondition));

            List<EntityWithMetadata<Cart>> results = entityService.search(
                    modelSpec, condition, Cart.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Cart> cartWithMetadata = results.get(0);
            Cart cart = cartWithMetadata.entity();
            cart.setStatus("CHECKING_OUT");
            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, "OPEN_CHECKOUT");
            
            logger.info("Checkout opened for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to open checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Getter
    @Setter
    public static class CartLineRequest {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
    }
}

