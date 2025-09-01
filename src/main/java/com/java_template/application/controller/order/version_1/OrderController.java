package com.java_template.application.controller.order.version_1;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        logger.info("Creating order with ID: {}", order.getOrderId());

        try {
            Order createdOrder = entityService.create(order);
            logger.info("Order created successfully with ID: {}", createdOrder.getOrderId());
            return ResponseEntity.ok(createdOrder);
        } catch (Exception e) {
            logger.error("Failed to create order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        logger.info("Retrieving order with ID: {}", orderId);

        try {
            Order order = entityService.findById(Order.class, orderId);
            if (order != null) {
                logger.info("Order found with ID: {}", orderId);
                return ResponseEntity.ok(order);
            } else {
                logger.warn("Order not found with ID: {}", orderId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<Order> getOrderByNumber(@PathVariable String orderNumber) {
        logger.info("Retrieving order with number: {}", orderNumber);

        try {
            List<Order> orders = entityService.findByField(Order.class, "orderNumber", orderNumber);
            if (!orders.isEmpty()) {
                Order order = orders.get(0);
                logger.info("Order found with number: {}", orderNumber);
                return ResponseEntity.ok(order);
            } else {
                logger.warn("Order not found with number: {}", orderNumber);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve order by number: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        logger.info("Retrieving all orders");

        try {
            List<Order> orders = entityService.findAll(Order.class);
            logger.info("Retrieved {} orders", orders.size());
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Failed to retrieve orders: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getOrdersByStatus(@PathVariable String status) {
        logger.info("Retrieving orders with status: {}", status);

        try {
            List<Order> orders = entityService.findByField(Order.class, "status", status);
            logger.info("Retrieved {} orders with status: {}", orders.size(), status);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Failed to retrieve orders by status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<Order> updateOrder(@PathVariable String orderId, @RequestBody Order order) {
        logger.info("Updating order with ID: {}", orderId);

        // Ensure the order ID in the path matches the order ID
        order.setOrderId(orderId);

        try {
            Order updatedOrder = entityService.update(order);
            logger.info("Order updated successfully with ID: {}", orderId);
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            logger.error("Failed to update order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{orderId}/fulfill")
    public ResponseEntity<Order> fulfillOrder(@PathVariable String orderId) {
        logger.info("Initiating fulfillment for order: {}", orderId);

        try {
            Order order = entityService.findById(Order.class, orderId);
            if (order == null) {
                logger.warn("Order not found with ID: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Update order status to trigger fulfillment workflow
            order.setStatus("PICKING");
            Order updatedOrder = entityService.update(order);
            
            logger.info("Order fulfillment initiated successfully");
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            logger.error("Failed to fulfill order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{orderId}/ship")
    public ResponseEntity<Order> shipOrder(@PathVariable String orderId) {
        logger.info("Initiating shipping for order: {}", orderId);

        try {
            Order order = entityService.findById(Order.class, orderId);
            if (order == null) {
                logger.warn("Order not found with ID: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Update order status to trigger shipping workflow
            order.setStatus("SENT");
            Order updatedOrder = entityService.update(order);
            
            logger.info("Order shipping initiated successfully");
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            logger.error("Failed to ship order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<Order> deliverOrder(@PathVariable String orderId) {
        logger.info("Marking order as delivered: {}", orderId);

        try {
            Order order = entityService.findById(Order.class, orderId);
            if (order == null) {
                logger.warn("Order not found with ID: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Update order status to delivered
            order.setStatus("DELIVERED");
            Order updatedOrder = entityService.update(order);
            
            logger.info("Order marked as delivered successfully");
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            logger.error("Failed to mark order as delivered: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
