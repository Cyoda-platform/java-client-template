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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Order controller providing REST endpoints for order creation
 * and status tracking throughout the fulfillment lifecycle.
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
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            String paymentState = paymentWithMetadata.metadata().getState();
            if (!"paid".equals(paymentState)) {
                logger.warn("Payment {} is not in paid state: {}", request.getPaymentId(), paymentState);
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order entity
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(generateShortUlid());
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

            // Set totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getGrandTotal());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Set guest contact
            if (cart.getGuestContact() != null) {
                Order.OrderGuestContact orderContact = new Order.OrderGuestContact();
                orderContact.setName(cart.getGuestContact().getName());
                orderContact.setEmail(cart.getGuestContact().getEmail());
                orderContact.setPhone(cart.getGuestContact().getPhone());

                if (cart.getGuestContact().getAddress() != null) {
                    Order.OrderAddress orderAddress = new Order.OrderAddress();
                    orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                    orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                    orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                    orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                    orderContact.setAddress(orderAddress);
                }
                order.setGuestContact(orderContact);
            }

            // Create order - this will trigger order processing
            EntityWithMetadata<Order> createdOrder = entityService.create(order);

            // Trigger order creation processor
            entityService.update(createdOrder.metadata().getId(), order, "create_order_from_paid");

            // Mark cart as converted
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus("WAITING_TO_FULFILL");

            logger.info("Created order {} from cart {} and payment {}", 
                       order.getOrderId(), request.getCartId(), request.getPaymentId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical UUID
     * GET /ui/order/{id}
     */
    @GetMapping("/{id}")
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
     * Get order by business ID
     * GET /ui/order/business/{orderId}
     */
    @GetMapping("/business/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderByBusinessId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessIdOrNull(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order by business ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (manual transitions)
     * PUT /ui/order/{orderId}/status?transition=TRANSITION_NAME
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestParam String transition) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessIdOrNull(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();
            order.setUpdatedAt(LocalDateTime.now());

            // Update order with specified transition
            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transition);

            logger.info("Updated order {} status with transition: {}", orderId, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all orders (for admin/ops use)
     * GET /ui/order
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Order>>> getAllOrders() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            List<EntityWithMetadata<Order>> orders = entityService.findAll(modelSpec, Order.class);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting all orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private String generateShortUlid() {
        // Simple ULID-like generator for demo purposes
        // In production, use a proper ULID library
        return "ORD-" + System.currentTimeMillis() % 1000000;
    }

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
