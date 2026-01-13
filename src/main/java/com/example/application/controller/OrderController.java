package com.example.application.controller;

import com.example.application.entity.order.version_1.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * OrderController - REST API for Order Management System
 * 
 * Provides endpoints for creating, retrieving, updating, and managing orders.
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
     * Create a new order
     * POST /ui/order
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Order>> createOrder(@Valid @RequestBody Order order) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, order.getOrderId(), "orderId", Order.class);

            if (existing != null) {
                logger.warn("Order with ID {} already exists", order.getOrderId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Order already exists with ID: %s", order.getOrderId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Order> response = entityService.create(order);
            logger.info("Order created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
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
     * Get order by technical UUID
     * GET /ui/order/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.getById(id, modelSpec, Order.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get order by business identifier
     * GET /ui/order/business/{orderId}
     */
    @GetMapping("/business/{orderId}")
    public ResponseEntity<EntityWithMetadata<Order>> getOrderByBusinessId(@PathVariable String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            EntityWithMetadata<Order> response = entityService.findByBusinessId(
                    modelSpec, orderId, "orderId", Order.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update order with optional workflow transition
     * PUT /ui/order/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Order>> updateOrder(
            @PathVariable UUID id,
            @Valid @RequestBody Order order,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Order> response = entityService.update(id, order, transition);
            logger.info("Order updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete order by technical UUID
     * DELETE /ui/order/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Order deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete order: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

