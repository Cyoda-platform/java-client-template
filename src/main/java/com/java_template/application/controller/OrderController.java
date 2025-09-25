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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order controller for order creation and management.
 * Creates orders from paid carts and provides order tracking.
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
     */
    @PostMapping("/create")
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        logger.info("Creating order from payment: {} and cart: {}", request.getPaymentId(), request.getCartId());

        try {
            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec();
            paymentModelSpec.setName(Payment.ENTITY_NAME);
            paymentModelSpec.setVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not PAID: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart has guest contact
            if (cart.getGuestContact() == null) {
                logger.warn("Cart {} does not have guest contact", request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Create order from cart data
            Order order = createOrderFromCart(cart);

            // Save order - this will trigger the CreateOrderFromPaid processor
            EntityWithMetadata<Order> savedOrder = entityService.create(order);

            // Mark cart as CONVERTED
            cart.setStatus("CONVERTED");
            cart.setUpdatedAt(LocalDateTime.now());
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            logger.info("Order {} created from cart {} with order number: {}", 
                       savedOrder.entity().getOrderId(), request.getCartId(), savedOrder.entity().getOrderNumber());

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(savedOrder.entity().getOrderId());
            response.setOrderNumber(savedOrder.entity().getOrderNumber());
            response.setStatus(savedOrder.entity().getStatus());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating order from payment: {} and cart: {}", 
                        request.getPaymentId(), request.getCartId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get order details
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        logger.info("Getting order: {}", orderId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found order: {} with status: {}", orderId, orderWithMetadata.entity().getStatus());
            return ResponseEntity.ok(orderWithMetadata.entity());

        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create order entity from cart data
     */
    private Order createOrderFromCart(Cart cart) {
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
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
                orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
                orderLines.add(orderLine);
            }
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

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return order;
    }

    /**
     * Generate a short ULID for order number (simplified for demo)
     */
    private String generateShortULID() {
        // Simple implementation - in production use proper ULID library
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Data
    public static class OrderCreateRequest {
        private String paymentId;
        private String cartId;
    }

    @Data
    public static class OrderCreateResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}
