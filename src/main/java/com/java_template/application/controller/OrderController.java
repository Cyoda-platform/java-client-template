package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.util.UlidGenerator;
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
 * Order Controller for OMS order management
 * Provides REST endpoints for order creation and status tracking
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
        logger.info("Creating order from payment: {} and cart: {}", request.getPaymentId(), request.getCartId());

        try {
            // Validate payment exists and is paid
            ModelSpec paymentModelSpec = new ModelSpec();
            paymentModelSpec.setName(Payment.ENTITY_NAME);
            paymentModelSpec.setVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);
            
            if (paymentWithMetadata == null) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not in PAID state: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart exists and matches payment
            ModelSpec cartModelSpec = new ModelSpec();
            cartModelSpec.setName(Cart.ENTITY_NAME);
            cartModelSpec.setVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                cartModelSpec, request.getCartId(), "cartId", Cart.class);
            
            if (cartWithMetadata == null) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();
            if (!request.getCartId().equals(payment.getCartId())) {
                logger.warn("Cart ID mismatch - payment cart: {}, request cart: {}", 
                           payment.getCartId(), request.getCartId());
                return ResponseEntity.badRequest().build();
            }

            // Generate unique order ID
            String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            // Create order entity from cart data
            Order order = new Order();
            order.setOrderId(orderId);
            order.setOrderNumber(UlidGenerator.generateShortUlid());
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Copy cart lines to order lines
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

            // Copy totals
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
                    Order.GuestAddress address = new Order.GuestAddress();
                    address.setLine1(cart.getGuestContact().getAddress().getLine1());
                    address.setCity(cart.getGuestContact().getAddress().getCity());
                    address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                    address.setCountry(cart.getGuestContact().getAddress().getCountry());
                    guestContact.setAddress(address);
                }
                
                order.setGuestContact(guestContact);
            }

            // Save order - this will trigger the workflow
            EntityWithMetadata<Order> savedOrder = entityService.create(order);
            UUID technicalId = savedOrder.metadata().getId();

            CreateOrderResponse response = new CreateOrderResponse();
            response.setOrderId(orderId);
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus("WAITING_TO_FULFILL");

            logger.info("Created order: {} with order number: {} (technical: {})", 
                       orderId, order.getOrderNumber(), technicalId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating order from payment: {} and cart: {}", 
                        request.getPaymentId(), request.getCartId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get order by order ID
     * GET /ui/order/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrder(@PathVariable String orderId) {
        logger.info("Getting order: {}", orderId);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(Order.ENTITY_NAME);
            modelSpec.setVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> order = entityService.findByBusinessId(modelSpec, orderId, "orderId", Order.class);
            
            if (order != null) {
                logger.info("Found order: {} with status: {}", orderId, order.entity().getStatus());
                return ResponseEntity.ok(order);
            } else {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for creating order
     */
    @Data
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;
    }

    /**
     * Response DTO for order creation
     */
    @Data
    public static class CreateOrderResponse {
        private String orderId;
        private String orderNumber;
        private String status;
    }
}
