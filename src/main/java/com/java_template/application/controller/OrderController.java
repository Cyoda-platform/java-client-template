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
 * Order Controller for order management
 * Provides endpoints for order creation and status retrieval
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
     * Create order from paid payment
     * POST /ui/order/create
     */
    @PostMapping("/create")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Validate payment exists and is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Validate payment is PAID
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not PAID: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order from cart
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(generateOrderNumber());
            order.setStatus("WAITING_TO_FULFILL");

            // Convert cart lines to order lines
            List<Order.OrderLine> orderLines = new ArrayList<>();
            if (cart.getLines() != null) {
                for (Cart.CartLine cartLine : cart.getLines()) {
                    Order.OrderLine orderLine = new Order.OrderLine();
                    orderLine.setSku(cartLine.getSku());
                    orderLine.setName(cartLine.getName());
                    orderLine.setUnitPrice(cartLine.getPrice());
                    orderLine.setQty(cartLine.getQty());
                    orderLine.setLineTotal(cartLine.getLineTotal());
                    orderLines.add(orderLine);
                }
            }
            order.setLines(orderLines);

            // Set totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getTotalItems());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Copy guest contact from cart
            if (cart.getGuestContact() != null) {
                Order.GuestContact guestContact = new Order.GuestContact();
                guestContact.setName(cart.getGuestContact().getName());
                guestContact.setEmail(cart.getGuestContact().getEmail());
                guestContact.setPhone(cart.getGuestContact().getPhone());
                
                if (cart.getGuestContact().getAddress() != null) {
                    Order.Address address = new Order.Address();
                    address.setLine1(cart.getGuestContact().getAddress().getLine1());
                    address.setCity(cart.getGuestContact().getAddress().getCity());
                    address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                    address.setCountry(cart.getGuestContact().getAddress().getCountry());
                    guestContact.setAddress(address);
                }
                
                order.setGuestContact(guestContact);
            }

            // Set timestamps
            LocalDateTime now = LocalDateTime.now();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // Create order in Cyoda (will trigger CreateOrderFromPaidProcessor)
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            // Mark cart as CONVERTED
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            OrderResponse response = new OrderResponse();
            response.setOrderId(orderResponse.entity().getOrderId());
            response.setOrderNumber(orderResponse.entity().getOrderNumber());
            response.setStatus(orderResponse.entity().getStatus());

            logger.info("Order {} created from payment {}", order.getOrderId(), request.getPaymentId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by ID
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order by ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (for fulfillment operations)
     * PUT /ui/order/{orderId}/status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateOrderStatusRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();

            // Update order with transition
            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, request.getTransition());

            logger.info("Order {} status updated with transition: {}", orderId, request.getTransition());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status for ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate short ULID-style order number
     */
    private String generateOrderNumber() {
        // Simple implementation - in production, use proper ULID library
        return "ORD-" + System.currentTimeMillis() % 1000000;
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
