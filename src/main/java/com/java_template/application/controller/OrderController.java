package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private EntityService entityService;

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            List<EntityResponse<Order>> orderResponses = entityService.findAll(
                Order.class,
                Order.ENTITY_NAME,
                Order.ENTITY_VERSION
            );

            List<Order> orders = orderResponses.stream()
                .map(EntityResponse::getData)
                .toList();

            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable UUID id) {
        try {
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            return ResponseEntity.ok(orderResponse.getData());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Order order) {
        try {
            EntityResponse<Order> savedOrder = entityService.save(order);
            
            Map<String, Object> response = Map.of(
                "id", savedOrder.getMetadata().getId(),
                "petId", savedOrder.getData().getPetId(),
                "customerName", savedOrder.getData().getCustomerName(),
                "state", savedOrder.getMetadata().getState(),
                "totalAmount", savedOrder.getData().getTotalAmount(),
                "message", "Order created successfully"
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateOrder(
            @PathVariable UUID id,
            @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        
        try {
            EntityResponse<Order> updatedOrder = entityService.update(id, order, transition);
            
            Map<String, Object> response = Map.of(
                "id", updatedOrder.getMetadata().getId(),
                "state", updatedOrder.getMetadata().getState(),
                "message", transition != null ? 
                    "Order updated with transition: " + transition : 
                    "Order updated successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable UUID id) {
        try {
            // Get the order first to update it with cancel transition
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            
            EntityResponse<Order> cancelledOrder = entityService.update(id, order, "cancel_order");
            
            Map<String, Object> response = Map.of(
                "id", cancelledOrder.getMetadata().getId(),
                "state", cancelledOrder.getMetadata().getState(),
                "message", "Order cancelled successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<Map<String, Object>> shipOrder(
            @PathVariable UUID id,
            @RequestBody Map<String, String> shippingData) {
        
        try {
            // Get the order first
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            
            // Update shipping information
            if (shippingData.containsKey("shippingMethod")) {
                order.setShippingMethod(shippingData.get("shippingMethod"));
            }
            if (shippingData.containsKey("trackingNumber")) {
                order.setTrackingNumber(shippingData.get("trackingNumber"));
            }
            order.setShipDate(LocalDateTime.now());
            
            // Update order with ship_order transition
            EntityResponse<Order> shippedOrder = entityService.update(id, order, "ship_order");
            
            Map<String, Object> response = Map.of(
                "id", shippedOrder.getMetadata().getId(),
                "state", shippedOrder.getMetadata().getState(),
                "trackingNumber", shippedOrder.getData().getTrackingNumber(),
                "shipDate", shippedOrder.getData().getShipDate().toString(),
                "message", "Order shipped successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/deliver")
    public ResponseEntity<Map<String, Object>> deliverOrder(@PathVariable UUID id) {
        try {
            // Get the order first
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            
            // Update order with deliver_order transition
            EntityResponse<Order> deliveredOrder = entityService.update(id, order, "deliver_order");
            
            Map<String, Object> response = Map.of(
                "id", deliveredOrder.getMetadata().getId(),
                "state", deliveredOrder.getMetadata().getState(),
                "complete", deliveredOrder.getData().getComplete(),
                "message", "Order delivered successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<Map<String, Object>> refundOrder(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> refundData) {
        
        try {
            // Get the order first
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            
            // Update order with refund_order transition
            order.setPaymentStatus("refunded");
            EntityResponse<Order> refundedOrder = entityService.update(id, order, "refund_order");
            
            Map<String, Object> response = Map.of(
                "id", refundedOrder.getMetadata().getId(),
                "state", refundedOrder.getMetadata().getState(),
                "paymentStatus", refundedOrder.getData().getPaymentStatus(),
                "message", "Order refunded successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
