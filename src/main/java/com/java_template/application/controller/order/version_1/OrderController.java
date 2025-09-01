package com.java_template.application.controller.order.version_1;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OrderController handles REST API endpoints for order operations.
 * This controller is a proxy to the EntityService for Order entities.
 */
@RestController
@RequestMapping("/ui/order")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private EntityService entityService;

    /**
     * Create order from paid payment.
     * 
     * @param request Order creation request
     * @return Created Order entity
     */
    @PostMapping("/create")
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        logger.info("Creating order from payment: {}", request.getPaymentId());

        try {
            // Validate payment exists and is paid
            SearchConditionRequest paymentCondition = SearchConditionRequest.group("and",
                Condition.of("paymentId", "equals", request.getPaymentId()));
            
            var paymentResponse = entityService.getFirstItemByCondition(Payment.class, paymentCondition, false);
            if (paymentResponse.isEmpty()) {
                logger.warn("Payment not found: {}", request.getPaymentId());
                return ResponseEntity.badRequest().build();
            }
            
            Payment payment = paymentResponse.get().getData();
            String paymentState = paymentResponse.get().getMetadata().getState();
            
            if (!"PAID".equals(paymentState)) {
                logger.warn("Payment is not in PAID state: {} (current state: {})", request.getPaymentId(), paymentState);
                return ResponseEntity.badRequest().build();
            }
            
            // Get cart
            SearchConditionRequest cartCondition = SearchConditionRequest.group("and",
                Condition.of("cartId", "equals", request.getCartId()));
            
            var cartResponse = entityService.getFirstItemByCondition(Cart.class, cartCondition, false);
            if (cartResponse.isEmpty()) {
                logger.warn("Cart not found: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            Cart cart = cartResponse.get().getData();
            
            // Validate cart has guest contact
            if (cart.getGuestContact() == null) {
                logger.warn("Cart does not have guest contact information: {}", request.getCartId());
                return ResponseEntity.badRequest().build();
            }
            
            // Create order entity
            String orderId = "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String orderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            
            Order order = new Order();
            order.setOrderId(orderId);
            order.setOrderNumber(orderNumber);
            order.setLines(convertCartLinesToOrderLines(cart.getLines()));
            
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getTotalItems());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);
            
            // Convert cart guest contact to order guest contact
            order.setGuestContact(convertCartGuestContactToOrderGuestContact(cart.getGuestContact()));
            
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            
            // Save order with CREATE_ORDER_FROM_PAID transition
            EntityResponse<Order> savedOrder = entityService.save(order);
            
            return ResponseEntity.ok(savedOrder.getData());
            
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get order details.
     * 
     * @param orderId Order identifier
     * @return Order entity
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        logger.info("Getting order: {}", orderId);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("orderId", "equals", orderId));
            
            var orderResponse = entityService.getFirstItemByCondition(Order.class, condition, false);
            
            if (orderResponse.isPresent()) {
                return ResponseEntity.ok(orderResponse.get().getData());
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
     * Update order (for status transitions).
     * 
     * @param orderId Order identifier
     * @param transition Workflow transition name (required)
     * @param order Order entity (optional updates)
     * @return Updated Order entity
     */
    @PutMapping("/{orderId}")
    public ResponseEntity<Order> updateOrder(
            @PathVariable String orderId,
            @RequestParam String transition,
            @RequestBody(required = false) Order order) {
        
        logger.info("Updating order: {} with transition: {}", orderId, transition);

        try {
            // Get existing order
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("orderId", "equals", orderId));
            
            var orderResponse = entityService.getFirstItemByCondition(Order.class, condition, false);
            if (orderResponse.isEmpty()) {
                logger.warn("Order not found: {}", orderId);
                return ResponseEntity.notFound().build();
            }
            
            Order existingOrder = orderResponse.get().getData();
            UUID entityId = orderResponse.get().getMetadata().getId();
            
            // If order data is provided, merge updates
            if (order != null) {
                // Only allow certain fields to be updated
                if (order.getGuestContact() != null) {
                    existingOrder.setGuestContact(order.getGuestContact());
                }
            }
            
            existingOrder.setUpdatedAt(LocalDateTime.now());
            
            // Update order with transition
            EntityResponse<Order> updatedOrder = entityService.update(entityId, existingOrder, transition);
            return ResponseEntity.ok(updatedOrder.getData());
            
        } catch (Exception e) {
            logger.error("Error updating order", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Helper methods
    private List<Order.OrderLine> convertCartLinesToOrderLines(List<Cart.CartLine> cartLines) {
        return cartLines.stream()
            .map(cartLine -> {
                BigDecimal lineTotal = cartLine.getPrice().multiply(BigDecimal.valueOf(cartLine.getQty()));
                return new Order.OrderLine(
                    cartLine.getSku(),
                    cartLine.getName(),
                    cartLine.getPrice(),
                    cartLine.getQty(),
                    lineTotal
                );
            })
            .collect(Collectors.toList());
    }

    private Order.GuestContact convertCartGuestContactToOrderGuestContact(Cart.GuestContact cartGuestContact) {
        Order.GuestContact orderGuestContact = new Order.GuestContact();
        orderGuestContact.setName(cartGuestContact.getName());
        orderGuestContact.setEmail(cartGuestContact.getEmail());
        orderGuestContact.setPhone(cartGuestContact.getPhone());
        
        if (cartGuestContact.getAddress() != null) {
            Order.Address orderAddress = new Order.Address();
            orderAddress.setLine1(cartGuestContact.getAddress().getLine1());
            orderAddress.setCity(cartGuestContact.getAddress().getCity());
            orderAddress.setPostcode(cartGuestContact.getAddress().getPostcode());
            orderAddress.setCountry(cartGuestContact.getAddress().getCountry());
            orderGuestContact.setAddress(orderAddress);
        }
        
        return orderGuestContact;
    }

    /**
     * Request DTO for creating order.
     */
    public static class CreateOrderRequest {
        private String paymentId;
        private String cartId;

        public String getPaymentId() {
            return paymentId;
        }

        public void setPaymentId(String paymentId) {
            this.paymentId = paymentId;
        }

        public String getCartId() {
            return cartId;
        }

        public void setCartId(String cartId) {
            this.cartId = cartId;
        }
    }
}
