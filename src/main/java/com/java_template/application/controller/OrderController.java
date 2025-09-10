package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OrderController - REST endpoints for order operations
 * 
 * Provides UI-facing APIs for order creation and retrieval.
 * Base path: /ui/order
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
     * POST /ui/order/create - Create order from paid cart and payment
     */
    @PostMapping("/create")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Find payment and validate it's PAID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);
            
            if (paymentWithMetadata == null) {
                logger.error("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            if (!"Paid".equals(paymentWithMetadata.metadata().getState())) {
                logger.error("Payment not in PAID state: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            // Find cart and validate it's CONVERTED
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.error("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            if (!"Converted".equals(cartWithMetadata.metadata().getState())) {
                logger.error("Cart not in CONVERTED state: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();
            Payment payment = paymentWithMetadata.entity();

            // Create order entity
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(generateOrderNumber());
            
            // Snapshot cart lines into order lines
            List<Order.OrderLine> orderLines = new ArrayList<>();
            for (Cart.CartLine cartLine : cart.getLines()) {
                Order.OrderLine orderLine = new Order.OrderLine();
                orderLine.setSku(cartLine.getSku());
                orderLine.setName(cartLine.getName());
                orderLine.setUnitPrice(cartLine.getPrice());
                orderLine.setQty(cartLine.getQty());
                orderLine.setLineTotal(cartLine.getPrice().multiply(BigDecimal.valueOf(cartLine.getQty())));
                orderLines.add(orderLine);
            }
            order.setLines(orderLines);

            // Set order totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getGrandTotal());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Snapshot guest contact
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

            LocalDateTime now = LocalDateTime.now();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // Create order with CREATE_ORDER_FROM_PAID transition
            EntityWithMetadata<Order> orderWithMetadata = entityService.create(order);
            
            logger.info("Order {} created from cart {} and payment {}", 
                       order.getOrderId(), request.getCartId(), request.getPaymentId());

            // Create response
            OrderResponse response = toOrderResponse(orderWithMetadata);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /ui/order/{orderId} - Get order details
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    orderModelSpec, orderId, "orderId", Order.class);
            
            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            OrderResponse response = toOrderResponse(orderWithMetadata);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate a short ULID-style order number
     */
    private String generateOrderNumber() {
        // Simple implementation - in production would use proper ULID library
        return "ORD-" + System.currentTimeMillis();
    }

    /**
     * Convert Order entity to response DTO
     */
    private OrderResponse toOrderResponse(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.entity();
        
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());
        response.setStatus(orderWithMetadata.metadata().getState());
        response.setLines(order.getLines());
        response.setTotals(order.getTotals());
        response.setGuestContact(order.getGuestContact());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        
        return response;
    }

    // Request/Response DTOs

    @Data
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;
    }

    @Data
    public static class OrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
        private List<Order.OrderLine> lines;
        private Order.OrderTotals totals;
        private Order.GuestContact guestContact;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
