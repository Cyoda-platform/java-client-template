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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order Controller - Handles order operations for OMS
 * 
 * Provides endpoints for:
 * - Order creation from paid cart
 * - Order status retrieval
 * - Order management
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
     * Create order from paid cart
     * POST /ui/order/create
     */
    @PostMapping("/create")
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            logger.info("Creating order from payment: {} and cart: {}", request.getPaymentId(), request.getCartId());

            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment not in PAID status: {} - Status: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart has guest contact
            if (cart.getGuestContact() == null || cart.getGuestContact().getName() == null) {
                logger.warn("Cart missing guest contact: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Create order entity
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(generateOrderNumber());
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

            // Set order totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getTotalItems());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Snapshot guest contact
            Order.OrderGuestContact guestContact = new Order.OrderGuestContact();
            guestContact.setName(cart.getGuestContact().getName());
            guestContact.setEmail(cart.getGuestContact().getEmail());
            guestContact.setPhone(cart.getGuestContact().getPhone());

            if (cart.getGuestContact().getAddress() != null) {
                Order.OrderGuestAddress address = new Order.OrderGuestAddress();
                address.setLine1(cart.getGuestContact().getAddress().getLine1());
                address.setCity(cart.getGuestContact().getAddress().getCity());
                address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                address.setCountry(cart.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            order.setGuestContact(guestContact);

            // Create order in Cyoda
            EntityWithMetadata<Order> createdOrder = entityService.create(order);

            // Trigger order processing with transition
            EntityWithMetadata<Order> processedOrder = entityService.update(
                    createdOrder.metadata().getId(), 
                    createdOrder.entity(), 
                    "create_order_from_paid");

            // Mark cart as CONVERTED
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(processedOrder.entity().getOrderId());
            response.setOrderNumber(processedOrder.entity().getOrderNumber());
            response.setStatus(processedOrder.entity().getStatus());

            logger.info("Order created: {} with number: {}", response.getOrderId(), response.getOrderNumber());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order from payment: {} and cart: {}", 
                        request.getPaymentId(), request.getCartId(), e);
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
            logger.info("Getting order: {}", orderId);

            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (order == null) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by order number
     * GET /ui/order/number/{orderNumber}
     */
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderByNumber(@PathVariable String orderNumber) {
        try {
            logger.info("Getting order by number: {}", orderNumber);

            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderNumber, "orderNumber", Order.class);

            if (order == null) {
                logger.warn("Order not found by number: {}", orderNumber);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order by number: {}", orderNumber, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate short ULID-style order number
     */
    private String generateOrderNumber() {
        // Simple implementation - in production, use proper ULID library
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + timestamp.substring(timestamp.length() - 6) + "-" + random;
    }

    // Request/Response DTOs

    @Getter
    @Setter
    public static class OrderCreateRequest {
        private String paymentId;
        private String cartId;
    }

    @Getter
    @Setter
    public static class OrderCreateResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}
