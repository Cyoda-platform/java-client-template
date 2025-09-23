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
 * Order Controller - REST endpoints for order management
 * 
 * Provides endpoints for order creation and retrieval
 * as specified in the OMS functional requirements.
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
            
            // Validate payment is PAID
            if (!"PAID".equals(payment.getStatus())) {
                logger.warn("Payment {} is not PAID: {}", request.getPaymentId(), payment.getStatus());
                return ResponseEntity.badRequest().build();
            }

            // Validate cart exists and matches payment
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null || !payment.getCartId().equals(request.getCartId())) {
                logger.warn("Cart validation failed for payment {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order entity by snapshotting cart data
            Order order = createOrderFromCart(cart, payment);

            // Create order with automatic processing
            EntityWithMetadata<Order> orderResponse = entityService.create(order);
            
            // Mark cart as CONVERTED
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");
            
            // Prepare response
            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(orderResponse.entity().getOrderId());
            response.setOrderNumber(orderResponse.entity().getOrderNumber());
            response.setStatus(orderResponse.entity().getStatus());
            
            logger.info("Created order {} from payment {} and cart {}", 
                       response.getOrderId(), request.getPaymentId(), request.getCartId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating order from payment: {}", request.getPaymentId(), e);
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
            logger.error("Error getting order by ID: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by technical ID
     * GET /ui/order/id/{id}
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);
            
            EntityWithMetadata<Order> order = entityService.getById(id, modelSpec, Order.class);
            return ResponseEntity.ok(order);
            
        } catch (Exception e) {
            logger.error("Error getting order by technical ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update order status (for fulfillment operations)
     * PUT /ui/order/{orderId}/status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody OrderStatusUpdateRequest request) {
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
            
            // Determine transition based on new status
            String transition = null;
            switch (request.getStatus()) {
                case "PICKING":
                    transition = "start_picking";
                    break;
                case "WAITING_TO_SEND":
                    transition = "ready_to_send";
                    break;
                case "SENT":
                    transition = "mark_sent";
                    break;
                case "DELIVERED":
                    transition = "mark_delivered";
                    break;
            }
            
            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transition);
            
            logger.info("Updated order {} status to {}", orderId, request.getStatus());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all orders (for admin/testing purposes)
     * GET /ui/order
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Order>>> getAllOrders() {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);
            
            List<EntityWithMetadata<Order>> orders = entityService.findAll(modelSpec, Order.class);
            return ResponseEntity.ok(orders);
            
        } catch (Exception e) {
            logger.error("Error getting all orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    /**
     * Creates an Order entity by snapshotting Cart data
     */
    private Order createOrderFromCart(Cart cart, Payment payment) {
        Order order = new Order();
        
        // Generate order identifiers
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderNumber(generateShortULID());
        order.setStatus("WAITING_TO_FULFILL");
        
        // Snapshot cart lines to order lines
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
        
        // Snapshot guest contact
        if (cart.getGuestContact() != null) {
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
        }
        
        // Set timestamps
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        
        return order;
    }

    /**
     * Generates a short ULID-like identifier for order numbers
     */
    private String generateShortULID() {
        // Simple implementation - in production, use proper ULID library
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
    }

    @Getter
    @Setter
    public static class OrderStatusUpdateRequest {
        private String status;
    }
}
