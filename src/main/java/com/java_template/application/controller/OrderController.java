package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
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
 * ABOUTME: This file contains the OrderController that exposes REST APIs for order management
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
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Validate payment exists and is PAID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessIdOrNull(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentResponse == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Payment not found: " + request.getPaymentId());
                return ResponseEntity.of(problemDetail).build();
            }

            Payment payment = paymentResponse.entity();
            if (!"PAID".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Payment must be PAID. Current status: " + payment.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart exists and matches payment
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessIdOrNull(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartResponse == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart not found: " + request.getCartId());
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartResponse.entity();
            if (!request.getCartId().equals(payment.getCartId())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Payment cart ID does not match request cart ID");
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart has guest contact
            if (cart.getGuestContact() == null || cart.getGuestContact().getName() == null || 
                cart.getGuestContact().getAddress() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart must have complete guest contact information");
                return ResponseEntity.of(problemDetail).build();
            }

            // Generate order ID and order number (short ULID)
            String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String orderNumber = generateShortULID();

            // Create order by snapshotting cart data
            Order order = new Order();
            order.setOrderId(orderId);
            order.setOrderNumber(orderNumber);
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Snapshot cart lines to order lines
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

            // Snapshot cart totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getTotalItems());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Snapshot guest contact
            Order.GuestContact guestContact = new Order.GuestContact();
            guestContact.setName(cart.getGuestContact().getName());
            guestContact.setEmail(cart.getGuestContact().getEmail());
            guestContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.Address address = new Order.Address();
                address.setLine1(cart.getGuestContact().getAddress().getLine1());
                address.setLine2(cart.getGuestContact().getAddress().getLine2());
                address.setCity(cart.getGuestContact().getAddress().getCity());
                address.setState(cart.getGuestContact().getAddress().getState());
                address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                address.setCountry(cart.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            order.setGuestContact(guestContact);

            // Create order entity - this will trigger CreateOrderFromPaid processor
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            // Mark cart as CONVERTED
            cart.setStatus("CONVERTED");
            entityService.update(cartResponse.metadata().getId(), cart, "checkout");

            logger.info("Order {} created from cart {} and payment {}", 
                    orderId, request.getCartId(), request.getPaymentId());

            // Return order summary
            CreateOrderResponse response = new CreateOrderResponse();
            response.setOrderId(orderId);
            response.setOrderNumber(orderNumber);
            response.setStatus("WAITING_TO_FULFILL");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by ID for confirmation/status
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessIdOrNull(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical UUID
     * GET /ui/order/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.getById(id, modelSpec, Order.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Generate a short ULID-like identifier for order numbers
     */
    private String generateShortULID() {
        // Simple implementation - in production, use a proper ULID library
        return "UL" + System.currentTimeMillis() % 1000000;
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;
    }

    @Getter
    @Setter
    public static class CreateOrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}
