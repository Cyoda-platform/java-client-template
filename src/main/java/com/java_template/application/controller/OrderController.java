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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Order controller providing order creation and management endpoints
 * for converting paid carts into orders and tracking order status.
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
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // Find payment by business ID
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessIdOrNull(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Payment not found: " + request.getPaymentId()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Payment payment = paymentWithMetadata.entity();

            // Validate payment is PAID
            if (!"PAID".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Payment must be PAID to create order. Current status: " + payment.getStatus()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Find cart by business ID
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessIdOrNull(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart not found: " + request.getCartId()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart has guest contact and items
            if (cart.getGuestContact() == null || cart.getLines() == null || cart.getLines().isEmpty()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart must have guest contact and items to create order"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Create order entity by snapshotting cart data
            Order order = createOrderFromCart(cart);

            // Create order with workflow transition
            EntityWithMetadata<Order> orderResponse = entityService.create(order);

            // The CreateOrderFromPaid processor will handle:
            // - Decrementing product inventory
            // - Creating shipment
            // - Setting order status to WAITING_TO_FULFILL

            // Mark cart as CONVERTED
            cart.setStatus("CONVERTED");
            cart.setUpdatedAt(LocalDateTime.now());
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            logger.info("Order {} created from cart {} and payment {}", 
                       order.getOrderId(), request.getCartId(), request.getPaymentId());

            // Return order summary
            OrderResponse response = new OrderResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(order.getStatus());
            response.setTotalAmount(order.getTotals().getGrand());

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
            logger.error("Error getting Order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get order by order ID (business identifier)
     * GET /ui/order/business/{orderId}
     */
    @GetMapping("/business/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderByOrderId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessIdOrNull(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Order by orderId: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get order by order number (customer-facing identifier)
     * GET /ui/order/number/{orderNumber}
     */
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderByOrderNumber(@PathVariable String orderNumber) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessIdOrNull(
                    modelSpec, orderNumber, "orderNumber", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Order by orderNumber: {}", orderNumber, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update order status (manual operations)
     * PUT /ui/order/{orderId}/status?transition=TRANSITION_NAME
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestParam String transition) {
        try {
            // Find order by business ID
            ModelSpec orderModelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessIdOrNull(
                    orderModelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderWithMetadata.entity();

            // Update order with specified transition
            order.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<Order> response = entityService.update(
                orderWithMetadata.metadata().getId(), 
                order, 
                transition
            );

            logger.info("Order {} status updated with transition {}", orderId, transition);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating order status", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods

    /**
     * Creates an Order entity by snapshotting Cart data
     */
    private Order createOrderFromCart(Cart cart) {
        Order order = new Order();
        
        // Generate order identifiers
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setOrderNumber(generateShortULID()); // Short ULID for customer reference
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
        
        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getGrandTotal());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);
        
        // Snapshot guest contact
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
        
        // Set timestamps
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        
        return order;
    }

    /**
     * Generates a short ULID-like identifier for customer-facing order numbers
     */
    private String generateShortULID() {
        // Simple implementation - in production use proper ULID library
        return "UL" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
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
        private Double totalAmount;
    }
}
