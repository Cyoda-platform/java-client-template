package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Order controller providing REST endpoints for order management
 * including order creation from paid carts and order status tracking.
 */
@RestController
@RequestMapping("/ui/order")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create order from paid cart
     * POST /ui/order/create
     */
    @PostMapping("/create")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        try {
            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Payment not found with ID: %s", request.getPaymentId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Payment must be PAID. Current status: %s", payment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart exists and matches payment
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart not found with ID: %s", request.getCartId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();
            if (!payment.getCartId().equals(cart.getCartId())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Payment and cart do not match"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Create order from cart
            Order order = createOrderFromCart(cart);

            EntityWithMetadata<Order> response = entityService.create(order);
            logger.info("Order created with ID: {} from cart: {}", order.getOrderId(), request.getCartId());

            CreateOrderResponse orderResponse = new CreateOrderResponse();
            orderResponse.setOrderId(order.getOrderId());
            orderResponse.setOrderNumber(order.getOrderNumber());
            orderResponse.setStatus(order.getStatus());

            return ResponseEntity.ok(orderResponse);
        } catch (Exception e) {
            logger.error("Failed to create order from payment: {} and cart: {}", request.getPaymentId(), request.getCartId(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get order by order ID
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Failed to retrieve order with ID: {}", orderId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve order with ID '%s': %s", orderId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update order status
     * PUT /ui/order/{orderId}/status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();
            order.setStatus(request.getStatus());
            order.setUpdatedAt(LocalDateTime.now());

            // Determine transition based on status
            String transition = getTransitionForStatus(request.getStatus());

            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transition);
            logger.info("Order status updated: {} -> {}", orderId, request.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update order status: {}", orderId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update order status '%s': %s", orderId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Create order entity from cart
     */
    private Order createOrderFromCart(Cart cart) {
        Order order = new Order();
        order.setOrderId(generateOrderId());
        order.setOrderNumber(generateOrderNumber());
        order.setStatus("WAITING_TO_FULFILL");

        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getLineTotal());
            orderLines.add(orderLine);
        }
        order.setLines(orderLines);

        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Copy guest contact
        if (cart.getGuestContact() != null) {
            Order.GuestContact guestContact = new Order.GuestContact();
            guestContact.setName(cart.getGuestContact().getName());
            guestContact.setEmail(cart.getGuestContact().getEmail());
            guestContact.setPhone(cart.getGuestContact().getPhone());

            if (cart.getGuestContact().getAddress() != null) {
                Order.GuestAddress address = new Order.GuestAddress();
                address.setLine1(cart.getGuestContact().getAddress().getLine1());
                address.setCity(cart.getGuestContact().getAddress().getCity());
                address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                address.setCountry(cart.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            order.setGuestContact(guestContact);
        }

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return order;
    }

    /**
     * Get workflow transition for order status
     */
    private String getTransitionForStatus(String status) {
        return switch (status) {
            case "PICKING" -> "start_picking";
            case "WAITING_TO_SEND" -> "ready_to_send";
            case "SENT" -> "mark_sent";
            case "DELIVERED" -> "mark_delivered";
            default -> null;
        };
    }

    /**
     * Generate unique order ID
     */
    private String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Generate short ULID for order number
     */
    private String generateOrderNumber() {
        // Simplified ULID generation - in practice you'd use a proper ULID library
        return "O" + System.currentTimeMillis() % 1000000;
    }

    /**
     * Request DTO for creating order
     */
    @Getter
    @Setter
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;
    }

    /**
     * Response DTO for order creation
     */
    @Getter
    @Setter
    public static class CreateOrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }

    /**
     * Request DTO for updating order status
     */
    @Getter
    @Setter
    public static class UpdateOrderStatusRequest {
        private String status;
    }
}
