package com.example.application.controller;

import com.example.application.entity.cart.version_1.Cart;
import com.example.application.entity.order.version_1.Order;
import com.example.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
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
 * OrderController - Order endpoints
 * POST /ui/order/create - Create order from paid payment
 * GET /ui/order/{orderId} - Get order
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
    public ResponseEntity<OrderCreateResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        try {
            // Get payment
            ModelSpec paymentModelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentWithMetadata = entityService.findByBusinessId(
                    paymentModelSpec, request.paymentId, "paymentId", Payment.class);

            if (paymentWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Payment payment = paymentWithMetadata.entity();
            if (!"PAID".equals(payment.getStatus())) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Payment is not in PAID status"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Get cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, request.cartId, "cartId", Cart.class);

            if (cartWithMetadata == null) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartWithMetadata.entity();

            // Create order
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setOrderNumber(generateULID());
            order.setStatus("WAITING_TO_FULFILL");

            // Copy lines from cart
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

            // Copy totals
            Order.Totals totals = new Order.Totals();
            totals.setItems(cart.getTotalItems());
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
                    address.setCity(cart.getGuestContact().getAddress().getCity());
                    address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                    address.setCountry(cart.getGuestContact().getAddress().getCountry());
                    guestContact.setAddress(address);
                }
                order.setGuestContact(guestContact);
            }

            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Order> createdOrder = entityService.create(order);
            logger.info("Order created: {}", createdOrder.entity().getOrderId());

            // Trigger CREATE_ORDER_FROM_PAID transition
            entityService.update(createdOrder.metadata().getId(), createdOrder.entity(), "CREATE_ORDER_FROM_PAID");

            // Mark cart as CONVERTED
            cart.setStatus("CONVERTED");
            entityService.update(cartWithMetadata.metadata().getId(), cart, "CHECKOUT");

            OrderCreateResponse response = new OrderCreateResponse();
            response.orderId = createdOrder.entity().getOrderId();
            response.orderNumber = createdOrder.entity().getOrderNumber();
            response.status = createdOrder.entity().getStatus();

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(createdOrder.metadata().getId())
                    .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
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
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Retrieved order: {}", orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve order '%s': %s", orderId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    private String generateULID() {
        // Simple ULID-like generation (short version)
        return UUID.randomUUID().toString().substring(0, 13).toUpperCase();
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
        public String orderId;
        public String orderNumber;
        public String status;
    }
}

