package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OrderController - UI-facing REST controller for order operations
 * 
 * This controller provides:
 * - Order creation from paid payment
 * - Order status retrieval
 * - Order confirmation details
 * 
 * Endpoints:
 * - POST /ui/order/create - Create order from paid payment
 * - GET /ui/order/{orderId} - Get order details
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
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            // Get payment and validate it's PAID
            EntityWithMetadata<Payment> paymentWithMetadata = getPaymentByPaymentId(request.getPaymentId());
            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not PAID: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            EntityWithMetadata<Cart> cartWithMetadata = getCartByCartId(request.getCartId());
            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            
            // Validate cart is CONVERTED
            if (!"CONVERTED".equals(cart.getStatus())) {
                logger.warn("Cart {} is not CONVERTED: {}", request.getCartId(), cart.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Create order from cart
            Order order = createOrderFromCart(cart, payment);

            EntityWithMetadata<Order> response = entityService.create(order);
            
            OrderCreateResponse createResponse = new OrderCreateResponse();
            createResponse.setOrderId(response.entity().getOrderId());
            createResponse.setOrderNumber(response.entity().getOrderNumber());
            createResponse.setStatus(response.entity().getStatus());
            
            logger.info("Order {} created from payment {} and cart {}", 
                       order.getOrderId(), request.getPaymentId(), request.getCartId());
            return ResponseEntity.ok(createResponse);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order details
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Order.ENTITY_NAME)
                .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> response = entityService.findByBusinessId(
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

    // Helper methods

    private EntityWithMetadata<Payment> getPaymentByPaymentId(String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Payment.ENTITY_NAME)
                .withVersion(Payment.ENTITY_VERSION);

            return entityService.findByBusinessId(modelSpec, paymentId, "paymentId", Payment.class);
        } catch (Exception e) {
            logger.error("Error finding payment by ID: {}", paymentId, e);
            return null;
        }
    }

    private EntityWithMetadata<Cart> getCartByCartId(String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);

            return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
        } catch (Exception e) {
            logger.error("Error finding cart by ID: {}", cartId, e);
            return null;
        }
    }

    private Order createOrderFromCart(Cart cart, Payment payment) {
        Order order = new Order();
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderNumber(generateShortULID());
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
        totals.setItems(cart.getGrandTotal());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);
        
        // Copy guest contact
        if (cart.getGuestContact() != null) {
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
        }
        
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        
        return order;
    }

    private String generateShortULID() {
        // Simple short ULID generation for demo purposes
        // In a real implementation, you would use a proper ULID library
        return "UL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
