package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.Customer;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@Slf4j
@AllArgsConstructor
public class Controller {

    private final EntityService entityService;

    // ----------- Workflow Endpoints -----------

    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (workflow == null || !workflow.isValid()) {
                log.error("Invalid Workflow entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid workflow data"));
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "workflow",
                    ENTITY_VERSION,
                    workflow);
            UUID technicalId = idFuture.get();
            log.info("Created Workflow with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createWorkflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating Workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "workflow",
                    ENTITY_VERSION,
                    technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Workflow not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Workflow not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Workflow id: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid workflow id"));
        } catch (Exception e) {
            log.error("Error retrieving Workflow with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // ----------- Order Endpoints -----------

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Order order) {
        try {
            if (order == null || !order.isValid()) {
                log.error("Invalid Order entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid order data"));
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "order",
                    ENTITY_VERSION,
                    order);
            UUID technicalId = idFuture.get();
            log.info("Created Order with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createOrder", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "order",
                    ENTITY_VERSION,
                    technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Order not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Order id: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid order id"));
        } catch (Exception e) {
            log.error("Error retrieving Order with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // ----------- Customer Endpoints -----------

    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(@RequestBody Customer customer) {
        try {
            if (customer == null || !customer.isValid()) {
                log.error("Invalid Customer entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid customer data"));
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "customer",
                    ENTITY_VERSION,
                    customer);
            UUID technicalId = idFuture.get();
            log.info("Created Customer with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createCustomer", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating Customer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "customer",
                    ENTITY_VERSION,
                    technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Customer not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Customer not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Customer id: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid customer id"));
        } catch (Exception e) {
            log.error("Error retrieving Customer with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }
}