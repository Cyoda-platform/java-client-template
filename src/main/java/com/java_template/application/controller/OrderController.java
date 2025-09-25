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
 * Handles order creation from paid carts and order status tracking
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
            // Validate payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);
            
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            String paymentStatus = paymentWithMetadata.metadata().getState();
            if (!"paid".equalsIgnoreCase(paymentStatus)) {
                return ResponseEntity.badRequest().build();
            }

            Payment payment = paymentWithMetadata.entity();

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

            // Validate cart has guest contact
            if (cart.getGuestContact() == null || cart.getGuestContact().getName() == null ||
                cart.getGuestContact().getAddress() == null) {
                return ResponseEntity.badRequest().build();
            }

            // Create order from cart
            Order order = createOrderFromCart(cart);

            EntityWithMetadata<Order> orderWithMetadata = entityService.create(order);

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(mapWorkflowStateToStatus(orderWithMetadata.metadata().getState()));

            logger.info("Order {} created from cart {} and payment {}", 
                       order.getOrderId(), request.getCartId(), request.getPaymentId());

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
    public ResponseEntity<OrderStatusResponse> getOrder(@PathVariable String orderId) {
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
            String status = orderWithMetadata.metadata().getState();

            OrderStatusResponse response = new OrderStatusResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(mapWorkflowStateToStatus(status));
            response.setLines(order.getLines());
            response.setTotals(order.getTotals());
            response.setGuestContact(order.getGuestContact());
            response.setCreatedAt(order.getCreatedAt());
            response.setUpdatedAt(order.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting order: {}", orderId, e);
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
            logger.error("Error getting order by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update order status (manual transitions)
     * POST /ui/order/{orderId}/transition/{transitionName}
     */
    @PostMapping("/{orderId}/transition/{transitionName}")
    public ResponseEntity<OrderStatusResponse> updateOrderStatus(
            @PathVariable String orderId,
            @PathVariable String transitionName) {
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

            // Update order with manual transition
            EntityWithMetadata<Order> updatedOrder = entityService.update(
                    orderWithMetadata.metadata().getId(), order, transitionName);

            OrderStatusResponse response = new OrderStatusResponse();
            response.setOrderId(order.getOrderId());
            response.setOrderNumber(order.getOrderNumber());
            response.setStatus(mapWorkflowStateToStatus(updatedOrder.metadata().getState()));
            response.setLines(order.getLines());
            response.setTotals(order.getTotals());
            response.setGuestContact(order.getGuestContact());
            response.setCreatedAt(order.getCreatedAt());
            response.setUpdatedAt(order.getUpdatedAt());

            logger.info("Order {} transitioned via {}", orderId, transitionName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order status: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create order entity from cart data
     */
    private Order createOrderFromCart(Cart cart) {
        Order order = new Order();
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderNumber(generateShortULID());
        
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
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Convert guest contact
        Order.OrderGuestContact guestContact = new Order.OrderGuestContact();
        guestContact.setName(cart.getGuestContact().getName());
        guestContact.setEmail(cart.getGuestContact().getEmail());
        guestContact.setPhone(cart.getGuestContact().getPhone());
        
        Order.OrderGuestAddress address = new Order.OrderGuestAddress();
        address.setLine1(cart.getGuestContact().getAddress().getLine1());
        address.setCity(cart.getGuestContact().getAddress().getCity());
        address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
        address.setCountry(cart.getGuestContact().getAddress().getCountry());
        guestContact.setAddress(address);
        
        order.setGuestContact(guestContact);

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return order;
    }

    /**
     * Generate short ULID for order number
     */
    private String generateShortULID() {
        // Simple implementation - in production use proper ULID library
        return "UL" + System.currentTimeMillis() % 1000000;
    }

    /**
     * Map workflow state to business status
     */
    private String mapWorkflowStateToStatus(String workflowState) {
        switch (workflowState.toLowerCase()) {
            case "initial":
            case "waiting_to_fulfill":
                return "WAITING_TO_FULFILL";
            case "picking":
                return "PICKING";
            case "waiting_to_send":
                return "WAITING_TO_SEND";
            case "sent":
                return "SENT";
            case "delivered":
                return "DELIVERED";
            default:
                return workflowState.toUpperCase();
        }
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
    public static class OrderStatusResponse {
        private String orderId;
        private String orderNumber;
        private String status;
        private List<Order.OrderLine> lines;
        private Order.OrderTotals totals;
        private Order.OrderGuestContact guestContact;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
