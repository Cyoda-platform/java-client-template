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
 * Order controller for order management
 * Handles order creation from paid payments and order status tracking
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
            // Get payment and validate it's PAID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
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
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart is CONVERTED and has guest contact
            if (!"CONVERTED".equals(cart.getStatus()) || cart.getGuestContact() == null) {
                return ResponseEntity.badRequest().build();
            }

            // Create order from cart data
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8));
            order.setOrderNumber(generateULID()); // Short ULID
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

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

            // Set order totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getGrandTotal());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Convert cart guest contact to order guest contact
            Order.GuestContact orderGuestContact = new Order.GuestContact();
            orderGuestContact.setName(cart.getGuestContact().getName());
            orderGuestContact.setEmail(cart.getGuestContact().getEmail());
            orderGuestContact.setPhone(cart.getGuestContact().getPhone());

            Order.Address orderAddress = new Order.Address();
            orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
            orderAddress.setLine2(cart.getGuestContact().getAddress().getLine2());
            orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
            orderAddress.setState(cart.getGuestContact().getAddress().getState());
            orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
            orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
            orderGuestContact.setAddress(orderAddress);
            order.setGuestContact(orderGuestContact);

            // Save order (will trigger workflow and processors)
            EntityWithMetadata<Order> orderWithMetadata = entityService.create(order);

            OrderResponse response = new OrderResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());

            logger.info("Created order {} (number: {}) from payment {} and cart {}", 
                       order.getOrderId(), order.getOrderNumber(), request.getPaymentId(), request.getCartId());
            return ResponseEntity.ok(response);
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
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    orderModelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(orderWithMetadata);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
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
     * Update order status
     * PUT /ui/order/{orderId}?transition=TRANSITION_NAME
     */
    @PutMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestParam String transition) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    orderModelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();
            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transition);

            logger.info("Updated order {} with transition: {}", orderId, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate a short ULID-like identifier
     */
    private String generateULID() {
        // Simple ULID-like generation for demo
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
    public static class OrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}
