package com.java_template.application.controller.order.version_1;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public CompletableFuture<ResponseEntity<Order>> createOrder(@RequestBody Order order) {
        logger.info("Creating order");

        return entityService.create(order)
            .thenApply(createdOrder -> {
                logger.info("Order created successfully with ID: {}", createdOrder.getOrderId());
                return ResponseEntity.ok(createdOrder);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to create order: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/{orderId}")
    public CompletableFuture<ResponseEntity<Order>> getOrder(@PathVariable String orderId) {
        logger.info("Retrieving order with ID: {}", orderId);

        return entityService.findById(Order.class, orderId)
            .thenApply(order -> {
                if (order != null) {
                    logger.info("Order found with ID: {}", orderId);
                    return ResponseEntity.ok(order);
                } else {
                    logger.warn("Order not found with ID: {}", orderId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve order: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/number/{orderNumber}")
    public CompletableFuture<ResponseEntity<Order>> getOrderByNumber(@PathVariable String orderNumber) {
        logger.info("Retrieving order with number: {}", orderNumber);

        return entityService.findByField(Order.class, "orderNumber", orderNumber)
            .thenApply(orders -> {
                if (!orders.isEmpty()) {
                    Order order = orders.get(0);
                    logger.info("Order found with number: {}", orderNumber);
                    return ResponseEntity.ok(order);
                } else {
                    logger.warn("Order not found with number: {}", orderNumber);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve order by number: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<Order>>> getAllOrders() {
        logger.info("Retrieving all orders");

        return entityService.findAll(Order.class)
            .thenApply(orders -> {
                logger.info("Retrieved {} orders", orders.size());
                return ResponseEntity.ok(orders);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve orders: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @GetMapping("/status/{status}")
    public CompletableFuture<ResponseEntity<List<Order>>> getOrdersByStatus(@PathVariable String status) {
        logger.info("Retrieving orders with status: {}", status);

        return entityService.findByField(Order.class, "status", status)
            .thenApply(orders -> {
                logger.info("Retrieved {} orders with status: {}", orders.size(), status);
                return ResponseEntity.ok(orders);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to retrieve orders by status: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PutMapping("/{orderId}")
    public CompletableFuture<ResponseEntity<Order>> updateOrder(@PathVariable String orderId, @RequestBody Order order) {
        logger.info("Updating order with ID: {}", orderId);

        // Ensure the order ID in the path matches the order ID
        order.setOrderId(orderId);

        return entityService.update(order)
            .thenApply(updatedOrder -> {
                logger.info("Order updated successfully with ID: {}", orderId);
                return ResponseEntity.ok(updatedOrder);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to update order: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PostMapping("/{orderId}/fulfill")
    public CompletableFuture<ResponseEntity<Order>> fulfillOrder(@PathVariable String orderId) {
        logger.info("Fulfilling order with ID: {}", orderId);

        return entityService.findById(Order.class, orderId)
            .thenCompose(order -> {
                if (order == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Update order status to trigger fulfillment workflow
                order.setStatus("PICKING");

                return entityService.update(order);
            })
            .thenApply(updatedOrder -> {
                if (updatedOrder != null) {
                    logger.info("Order fulfillment initiated successfully");
                    return ResponseEntity.ok(updatedOrder);
                } else {
                    logger.warn("Order not found with ID: {}", orderId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to fulfill order: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PostMapping("/{orderId}/ship")
    public CompletableFuture<ResponseEntity<Order>> shipOrder(@PathVariable String orderId) {
        logger.info("Shipping order with ID: {}", orderId);

        return entityService.findById(Order.class, orderId)
            .thenCompose(order -> {
                if (order == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Update order status to trigger shipping workflow
                order.setStatus("SENT");

                return entityService.update(order);
            })
            .thenApply(updatedOrder -> {
                if (updatedOrder != null) {
                    logger.info("Order shipping initiated successfully");
                    return ResponseEntity.ok(updatedOrder);
                } else {
                    logger.warn("Order not found with ID: {}", orderId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to ship order: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @PostMapping("/{orderId}/deliver")
    public CompletableFuture<ResponseEntity<Order>> deliverOrder(@PathVariable String orderId) {
        logger.info("Marking order as delivered with ID: {}", orderId);

        return entityService.findById(Order.class, orderId)
            .thenCompose(order -> {
                if (order == null) {
                    return CompletableFuture.completedFuture(null);
                }

                // Update order status to delivered
                order.setStatus("DELIVERED");

                return entityService.update(order);
            })
            .thenApply(updatedOrder -> {
                if (updatedOrder != null) {
                    logger.info("Order marked as delivered successfully");
                    return ResponseEntity.ok(updatedOrder);
                } else {
                    logger.warn("Order not found with ID: {}", orderId);
                    return ResponseEntity.notFound().build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Failed to mark order as delivered: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
}