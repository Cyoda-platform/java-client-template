package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Order controller providing REST endpoints for order creation
 * from paid payments and order status retrieval.
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
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        try {
            // Validate payment exists and is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Payment not found with ID: %s", request.getPaymentId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Payment must be PAID to create order. Current status: %s", payment.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Validate cart exists
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Cart not found with ID: %s", request.getCartId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();

            // Validate cart has guest contact
            if (cart.getGuestContact() == null || cart.getGuestContact().getName() == null || 
                cart.getGuestContact().getAddress() == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Cart must have guest contact information to create order"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Create order from cart
            Order order = createOrderFromCart(cart, payment);

            EntityWithMetadata<Order> createdOrder = entityService.create(order);

            // Trigger order processing (stock decrement, shipment creation)
            EntityWithMetadata<Order> processedOrder = entityService.update(
                    createdOrder.metadata().getId(), 
                    createdOrder.entity(), 
                    "create_order_from_paid");

            // Mark cart as converted
            entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");

            OrderResponse response = new OrderResponse();
            response.setOrderId(processedOrder.entity().getOrderId());
            response.setOrderNumber(processedOrder.entity().getOrderNumber());
            response.setStatus(processedOrder.entity().getStatus());

            logger.info("Order created from payment {}: {}", request.getPaymentId(), response.getOrderId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(response.getOrderId())
                .toUri();

            return ResponseEntity.created(location).body(response);

        } catch (Exception e) {
            logger.error("Failed to create order from payment: {}", request.getPaymentId(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get order by ID
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
            logger.error("Failed to get order: {}", orderId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to get order '%s': %s", orderId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update order status (for fulfillment operations)
     * POST /ui/order/{orderId}/status
     */
    @PostMapping("/{orderId}/status")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrderStatus(
            @PathVariable String orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            EntityWithMetadata<Order> orderWithMetadata = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (orderWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            // Map status to transition
            String transition = mapStatusToTransition(request.getStatus());
            if (transition == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Invalid status: %s", request.getStatus())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Order> response = entityService.update(
                    orderWithMetadata.metadata().getId(), 
                    orderWithMetadata.entity(), 
                    transition);

            logger.info("Order status updated: {} -> {}", orderId, request.getStatus());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to update order status: {}", orderId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update order status: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Create order entity from cart and payment
     */
    private Order createOrderFromCart(Cart cart, Payment payment) {
        Order order = new Order();
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderNumber(generateShortUlid());
        order.setStatus("WAITING_TO_FULFILL");
        order.setCreatedAt(LocalDateTime.now());

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

        // Copy guest contact
        Order.GuestContact guestContact = new Order.GuestContact();
        guestContact.setName(cart.getGuestContact().getName());
        guestContact.setEmail(cart.getGuestContact().getEmail());
        guestContact.setPhone(cart.getGuestContact().getPhone());

        Order.GuestAddress address = new Order.GuestAddress();
        address.setLine1(cart.getGuestContact().getAddress().getLine1());
        address.setCity(cart.getGuestContact().getAddress().getCity());
        address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
        address.setCountry(cart.getGuestContact().getAddress().getCountry());
        guestContact.setAddress(address);

        order.setGuestContact(guestContact);

        return order;
    }

    /**
     * Generate short ULID for order number
     */
    private String generateShortUlid() {
        return "UL" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * Map status to workflow transition
     */
    private String mapStatusToTransition(String status) {
        return switch (status) {
            case "PICKING" -> "start_picking";
            case "WAITING_TO_SEND" -> "ready_to_send";
            case "SENT" -> "mark_sent";
            case "DELIVERED" -> "mark_delivered";
            default -> null;
        };
    }

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
    }

    @Data
    public static class UpdateOrderStatusRequest {
        private String status;
    }
}
