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
 * Order Controller - REST API for order management
 * 
 * Provides endpoints for:
 * - Creating orders from paid payments
 * - Reading order status and details
 * - Managing order lifecycle
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
    public ResponseEntity<CreateOrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Validate payment exists and is PAID
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
                logger.warn("Payment {} not in PAID status: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart exists and matches payment
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
            
            if (!payment.getCartId().equals(cart.getCartId())) {
                logger.warn("Payment cart ID {} does not match request cart ID {}", 
                        payment.getCartId(), cart.getCartId());
                return ResponseEntity.badRequest().build();
            }

            if (!"CHECKING_OUT".equals(cart.getStatus())) {
                logger.warn("Cart {} not in CHECKING_OUT status: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart has guest contact
            if (cart.getGuestContact() == null || cart.getGuestContact().getName() == null ||
                cart.getGuestContact().getAddress() == null) {
                logger.warn("Cart {} missing required guest contact information", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Generate order ID
            String orderId = "ORDER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            // Create order from cart
            Order order = createOrderFromCart(orderId, cart);

            // Create order entity
            EntityWithMetadata<Order> orderResponse = entityService.create(order);
            
            // Convert cart to CONVERTED status
            cart.setStatus("CONVERTED");
            cart.setUpdatedAt(LocalDateTime.now());
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            logger.info("Order created: {} from cart: {} payment: {}", 
                    orderId, request.getCartId(), request.getPaymentId());

            CreateOrderResponse response = new CreateOrderResponse();
            response.setOrderId(orderId);
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());
            
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
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);
            
            EntityWithMetadata<Order> order = entityService.findByBusinessId(
                    modelSpec, orderNumber, "orderNumber", Order.class);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error getting order by number: {}", orderNumber, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status
     * PUT /ui/order/{orderId}/status?transition=TRANSITION_NAME
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestParam String transition) {
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
            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transition);
            
            logger.info("Order {} status updated with transition: {}", orderId, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create order entity from cart
     */
    private Order createOrderFromCart(String orderId, Cart cart) {
        Order order = new Order();
        order.setOrderId(orderId);
        
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

        // Convert guest contact
        Order.OrderGuestContact guestContact = new Order.OrderGuestContact();
        guestContact.setName(cart.getGuestContact().getName());
        guestContact.setEmail(cart.getGuestContact().getEmail());
        guestContact.setPhone(cart.getGuestContact().getPhone());
        
        if (cart.getGuestContact().getAddress() != null) {
            Order.OrderAddress address = new Order.OrderAddress();
            address.setLine1(cart.getGuestContact().getAddress().getLine1());
            address.setCity(cart.getGuestContact().getAddress().getCity());
            address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
            address.setCountry(cart.getGuestContact().getAddress().getCountry());
            guestContact.setAddress(address);
        }
        order.setGuestContact(guestContact);

        return order;
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
