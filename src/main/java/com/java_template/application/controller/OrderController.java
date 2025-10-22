package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * ABOUTME: OrderController exposes REST APIs for order operations
 * including creating orders from paid payments and retrieving order details.
 */
@RestController
@RequestMapping("/ui/order")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create order from paid payment
     * POST /ui/order/create
     * Body: { "paymentId": "...", "cartId": "..." }
     * Returns: { "orderId": "...", "orderNumber": "...", "status": "..." }
     */
    @PostMapping("/create")
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            // Verify payment is PAID
            ModelSpec paymentModelSpec = new ModelSpec()
                    .withName(Payment.ENTITY_NAME)
                    .withVersion(Payment.ENTITY_VERSION);

            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.getPaymentId(), "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            String paymentState = paymentWithMetadata.metadata().getState();
            if (!"paid".equalsIgnoreCase(paymentState)) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Payment is not in PAID state: " + paymentState
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Get cart to snapshot lines and contact
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.getCartId(), "cartId", Cart.class);

            if (cartWithMetadata == null) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        "Cart not found"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            order.setOrderNumber(generateOrderNumber());
            
            // Snapshot cart lines
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
            totals.setItems(cart.getTotalItems());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Snapshot guest contact
            if (cart.getGuestContact() != null) {
                Order.GuestContact contact = new Order.GuestContact();
                contact.setName(cart.getGuestContact().getName());
                contact.setEmail(cart.getGuestContact().getEmail());
                contact.setPhone(cart.getGuestContact().getPhone());
                
                if (cart.getGuestContact().getAddress() != null) {
                    Order.Address address = new Order.Address();
                    address.setLine1(cart.getGuestContact().getAddress().getLine1());
                    address.setCity(cart.getGuestContact().getAddress().getCity());
                    address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                    address.setCountry(cart.getGuestContact().getAddress().getCountry());
                    contact.setAddress(address);
                }
                order.setGuestContact(contact);
            }

            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Create order in Cyoda
            EntityWithMetadata<Order> createdOrder = entityService.create(order);
            
            // Trigger create_order_from_paid transition
            entityService.update(createdOrder.metadata().getId(), createdOrder.entity(), "create_order_from_paid");

            OrderCreateResponse response = new OrderCreateResponse();
            response.setOrderId(createdOrder.entity().getOrderId());
            response.setOrderNumber(createdOrder.entity().getOrderNumber());
            response.setStatus(createdOrder.metadata().getState());

            logger.info("Order created: {} ({})", createdOrder.entity().getOrderId(), createdOrder.entity().getOrderNumber());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Failed to create order: " + e.getMessage()
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
            logger.error("Error retrieving order: {}", orderId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Failed to retrieve order: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    private String generateOrderNumber() {
        // Generate short ULID-like order number
        return "ORD-" + System.currentTimeMillis();
    }

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

