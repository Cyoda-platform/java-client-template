package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for Order operations.
 * Provides endpoints for managing orders in the store.
 */
@RestController
@RequestMapping("/store")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Place an order for a pet
     */
    @PostMapping("/order")
    public CompletableFuture<ResponseEntity<Order>> placeOrder(@Valid @RequestBody Order order) {
        logger.info("Placing new order for pet ID: {}", order.getPetId());
        
        return entityService.addItem(Order.ENTITY_NAME, Order.ENTITY_VERSION, order)
            .thenCompose(entityId -> entityService.getItem(entityId))
            .thenApply(dataPayload -> {
                try {
                    Order savedOrder = objectMapper.treeToValue(dataPayload.getData(), Order.class);
                    return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder);
                } catch (Exception e) {
                    logger.error("Error converting saved order data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Order>build();
                }
            });
    }

    /**
     * Get order by ID
     */
    @GetMapping("/order/{orderId}")
    public CompletableFuture<ResponseEntity<Order>> getOrderById(@PathVariable Long orderId) {
        logger.info("Getting order by ID: {}", orderId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(orderId.toString().getBytes());
        
        return entityService.getItem(entityId)
            .thenApply(dataPayload -> {
                try {
                    Order order = objectMapper.treeToValue(dataPayload.getData(), Order.class);
                    return ResponseEntity.ok(order);
                } catch (Exception e) {
                    logger.error("Error converting order data", e);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).<Order>build();
                }
            });
    }

    /**
     * Update order status
     */
    @PutMapping("/order/{orderId}")
    public CompletableFuture<ResponseEntity<Order>> updateOrder(
            @PathVariable Long orderId, 
            @Valid @RequestBody Order order) {
        logger.info("Updating order with ID: {}", orderId);
        
        // Set the ID from path parameter
        order.setId(orderId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(orderId.toString().getBytes());
        
        return entityService.updateItem(entityId, order)
            .thenCompose(updatedId -> entityService.getItem(updatedId))
            .thenApply(dataPayload -> {
                try {
                    Order updatedOrder = objectMapper.treeToValue(dataPayload.getData(), Order.class);
                    return ResponseEntity.ok(updatedOrder);
                } catch (Exception e) {
                    logger.error("Error converting updated order data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Order>build();
                }
            });
    }

    /**
     * Delete purchase order by ID
     */
    @DeleteMapping("/order/{orderId}")
    public CompletableFuture<ResponseEntity<Void>> deleteOrder(@PathVariable Long orderId) {
        logger.info("Deleting order by ID: {}", orderId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(orderId.toString().getBytes());
        
        return entityService.deleteItem(entityId)
            .thenApply(deletedId -> ResponseEntity.ok().<Void>build());
    }

    /**
     * Get inventory by status
     */
    @GetMapping("/inventory")
    public CompletableFuture<ResponseEntity<Map<String, Integer>>> getInventory() {
        logger.info("Getting inventory by status");
        
        return entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null)
            .thenApply(dataPayloads -> {
                try {
                    Map<String, Integer> inventory = new HashMap<>();
                    inventory.put("available", dataPayloads.size()); // Simplified inventory count
                    inventory.put("pending", 0);
                    inventory.put("sold", 0);
                    
                    return ResponseEntity.ok(inventory);
                } catch (Exception e) {
                    logger.error("Error getting inventory", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Map<String, Integer>>build();
                }
            });
    }
}
