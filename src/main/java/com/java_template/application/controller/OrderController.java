package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;

    public OrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Order>> createOrder(@RequestBody Order order) {
        try {
            EntityResponse<Order> response = entityService.save(order);
            logger.info("Order created with ID: {}", response.getMetadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Order>> getOrder(@PathVariable UUID id) {
        try {
            EntityResponse<Order> response = entityService.getItem(id, Order.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving order with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/orderId/{orderId}")
    public ResponseEntity<EntityResponse<Order>> getOrderByOrderId(@PathVariable String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Order>> response = entityService.getFirstItemByCondition(
                Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving order with orderId: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/orderNumber/{orderNumber}")
    public ResponseEntity<EntityResponse<Order>> getOrderByOrderNumber(@PathVariable String orderNumber) {
        try {
            Condition orderNumberCondition = Condition.of("$.orderNumber", "EQUALS", orderNumber);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderNumberCondition));

            Optional<EntityResponse<Order>> response = entityService.getFirstItemByCondition(
                Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, condition, true);
            
            if (response.isPresent()) {
                return ResponseEntity.ok(response.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving order with orderNumber: {}", orderNumber, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Order>>> getAllOrders() {
        try {
            List<EntityResponse<Order>> orders = entityService.findAll(Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error retrieving all orders", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Order>> updateOrder(
            @PathVariable UUID id, 
            @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        try {
            EntityResponse<Order> response = entityService.update(id, order, transition);
            logger.info("Order updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Order deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/start-picking")
    public ResponseEntity<EntityResponse<Order>> startPickingOrder(@PathVariable UUID id) {
        try {
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            EntityResponse<Order> response = entityService.update(id, order, "START_PICKING");
            logger.info("Order picking started with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error starting picking for order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/ready-to-send")
    public ResponseEntity<EntityResponse<Order>> markOrderReadyToSend(@PathVariable UUID id) {
        try {
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            EntityResponse<Order> response = entityService.update(id, order, "READY_TO_SEND");
            logger.info("Order marked ready to send with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking order ready to send with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<EntityResponse<Order>> sendOrder(@PathVariable UUID id) {
        try {
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            EntityResponse<Order> response = entityService.update(id, order, "SEND");
            logger.info("Order sent with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error sending order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/deliver")
    public ResponseEntity<EntityResponse<Order>> deliverOrder(@PathVariable UUID id) {
        try {
            EntityResponse<Order> orderResponse = entityService.getItem(id, Order.class);
            Order order = orderResponse.getData();
            EntityResponse<Order> response = entityService.update(id, order, "DELIVER");
            logger.info("Order delivered with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error delivering order with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
