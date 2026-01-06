package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
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
 * OrderController - Order Management API
 * Endpoints: POST /ui/order/create, GET /ui/order/{orderId}
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
     */
    @PostMapping("/create")
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            // Verify payment is PAID
            ModelSpec paymentSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            
            SimpleCondition paymentIdCondition = new SimpleCondition()
                    .withJsonPath("$.paymentId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(request.getPaymentId()));

            GroupCondition paymentCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(paymentIdCondition));

            List<EntityWithMetadata<Payment>> paymentResults = entityService.search(
                    paymentSpec, paymentCondition, Payment.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (paymentResults.isEmpty() || !paymentResults.get(0).entity().getStatus().equals("PAID")) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Payment not found or not in PAID status"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            Payment payment = paymentResults.get(0).entity();

            // Get cart
            ModelSpec cartSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            SimpleCondition cartIdCondition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(payment.getCartId()));

            GroupCondition cartCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cartIdCondition));

            List<EntityWithMetadata<Cart>> cartResults = entityService.search(
                    cartSpec, cartCondition, Cart.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (cartResults.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Cart cart = cartResults.get(0).entity();

            // Create order
            Order order = new Order();
            order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setOrderNumber(generateULID());
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

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

            // Set totals
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

            // Create order with transition to trigger CreateOrderFromPaidProcessor
            EntityWithMetadata<Order> response = entityService.create(order);
            
            // Trigger the processor via transition
            entityService.update(response.metadata().getId(), response.entity(), "CREATE_ORDER_FROM_PAID");
            
            logger.info("Order created with ID: {}", response.entity().getOrderId());

            OrderCreateResponse orderResponse = new OrderCreateResponse();
            orderResponse.setOrderId(response.entity().getOrderId());
            orderResponse.setOrderNumber(response.entity().getOrderNumber());
            orderResponse.setStatus(response.entity().getStatus());
            
            return ResponseEntity.ok(orderResponse);
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
            
            SimpleCondition orderIdCondition = new SimpleCondition()
                    .withJsonPath("$.orderId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(orderId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(orderIdCondition));

            List<EntityWithMetadata<Order>> results = entityService.search(
                    modelSpec, condition, Order.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Retrieved order with ID: {}", orderId);
            return ResponseEntity.ok(results.get(0));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Generate short ULID-like order number
     */
    private String generateULID() {
        return UUID.randomUUID().toString().substring(0, 26).toUpperCase();
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

