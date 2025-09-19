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
 * Order Controller - Order management for OMS
 * 
 * Provides REST endpoints for creating orders from paid carts and retrieving order status.
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
            logger.info("Creating order from payment: {} and cart: {}", request.getPaymentId(), request.getCartId());

            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not PAID: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Get cart data
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart has guest contact
            if (cart.getGuestContact() == null || cart.getGuestContact().getAddress() == null) {
                logger.warn("Cart {} does not have complete guest contact information", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Create order entity with cart data snapshot
            Order order = createOrderFromCart(cart);

            // Create order with CREATE_ORDER_FROM_PAID transition
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            // Mark cart as CONVERTED
            entityService.update(cartWithMetadata.metadata().getId(), cart, "CHECKOUT");

            logger.info("Order {} created from cart {} with order number: {}", 
                       order.getOrderId(), request.getCartId(), order.getOrderNumber());

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());
            response.setTotalItems(order.getTotals().getItems());
            response.setGrandTotal(order.getTotals().getGrand());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating order from payment: {} and cart: {}", 
                        request.getPaymentId(), request.getCartId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by orderId
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

            logger.info("Found order: {} with status: {}", orderId, order.entity().getStatus());
            return ResponseEntity.ok(order);

        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create order entity from cart data (snapshot)
     */
    private Order createOrderFromCart(Cart cart) {
        Order order = new Order();
        
        // Generate order identifiers
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setOrderNumber(generateShortULID());
        order.setStatus("WAITING_TO_FULFILL");
        
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
        order.setGuestContact(convertToOrderGuestContact(cart.getGuestContact()));
        
        // Set timestamps
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        
        return order;
    }

    /**
     * Convert cart guest contact to order guest contact
     */
    private Order.GuestContact convertToOrderGuestContact(Cart.GuestContact cartContact) {
        Order.GuestContact orderContact = new Order.GuestContact();
        orderContact.setName(cartContact.getName());
        orderContact.setEmail(cartContact.getEmail());
        orderContact.setPhone(cartContact.getPhone());
        
        if (cartContact.getAddress() != null) {
            Order.GuestAddress orderAddress = new Order.GuestAddress();
            orderAddress.setLine1(cartContact.getAddress().getLine1());
            orderAddress.setLine2(cartContact.getAddress().getLine2());
            orderAddress.setCity(cartContact.getAddress().getCity());
            orderAddress.setState(cartContact.getAddress().getState());
            orderAddress.setPostcode(cartContact.getAddress().getPostcode());
            orderAddress.setCountry(cartContact.getAddress().getCountry());
            orderContact.setAddress(orderAddress);
        }
        
        return orderContact;
    }

    /**
     * Generate short ULID for order number
     * For demo purposes, using a simple format
     */
    private String generateShortULID() {
        return "UL" + System.currentTimeMillis() % 1000000;
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
        private Integer totalItems;
        private Double grandTotal;
    }
}
