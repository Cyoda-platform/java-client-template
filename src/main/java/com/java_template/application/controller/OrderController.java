package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * ABOUTME: Order controller providing REST APIs for order management including
 * order creation from paid payments and order status tracking.
 */
@RestController
@RequestMapping("/ui/order")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create order from paid payment
     * POST /ui/order/create
     */
    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Validate payment exists and is PAID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Payment not found: " + request.getPaymentId());
                return ResponseEntity.of(problemDetail).build();
            }

            Payment payment = paymentWithMetadata.entity();

            if (!"PAID".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Payment must be PAID. Current status: " + payment.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, payment.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart not found: " + payment.getCartId());
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is CONVERTED and has guest contact
            if (!"CONVERTED".equals(cart.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, 
                    "Cart must be CONVERTED. Current status: " + cart.getStatus());
                return ResponseEntity.of(problemDetail).build();
            }

            if (cart.getGuestContact() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Cart must have guest contact information");
                return ResponseEntity.of(problemDetail).build();
            }

            // Create order from cart data
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setOrderNumber(generateShortUlid());
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

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

            // Set order totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getGrandTotal());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Convert cart guest contact to order guest contact
            Order.OrderGuestContact orderGuestContact = new Order.OrderGuestContact();
            orderGuestContact.setName(cart.getGuestContact().getName());
            orderGuestContact.setEmail(cart.getGuestContact().getEmail());
            orderGuestContact.setPhone(cart.getGuestContact().getPhone());

            Order.OrderGuestAddress orderAddress = new Order.OrderGuestAddress();
            orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
            orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
            orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
            orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
            orderGuestContact.setAddress(orderAddress);
            order.setGuestContact(orderGuestContact);

            // Create order (this will trigger CreateOrderFromPaid processor)
            EntityWithMetadata<Order> response = entityService.create(order);
            
            logger.info("Order {} created from payment {}", order.getOrderId(), request.getPaymentId());
            
            // Return order summary
            OrderResponse orderResponse = new OrderResponse();
            orderResponse.setOrderId(order.getOrderId());
            orderResponse.setOrderNumber(order.getOrderNumber());
            orderResponse.setStatus(order.getStatus());
            
            return ResponseEntity.ok(orderResponse);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
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
            EntityWithMetadata<Order> response = entityService.findByBusinessIdOrNull(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (manual transitions)
     * PUT /ui/order/{orderId}/status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateOrderStatusRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessIdOrNull(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();

            // Update order with manual transition
            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, request.getTransition());
            
            logger.info("Order {} status updated with transition: {}", orderId, request.getTransition());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate short ULID for order number (simplified version)
     */
    private String generateShortUlid() {
        return "UL" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
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
    public static class OrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }

    @Getter
    @Setter
    public static class UpdateOrderStatusRequest {
        private String transition;
    }
}
