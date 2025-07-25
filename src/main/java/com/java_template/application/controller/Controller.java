package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Customer;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.Workflow;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // --------- Workflow Endpoints ---------
    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@Valid @RequestBody Workflow workflow) throws JsonProcessingException {
        if (workflow == null || !workflow.isValid()) {
            logger.error("Invalid Workflow data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Workflow data"));
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Workflow", ENTITY_VERSION, workflow);
        UUID technicalId = idFuture.join();

        logger.info("Workflow created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Workflow", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Workflow not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }
            Workflow workflow = objectMapper.treeToValue(node, Workflow.class);
            return ResponseEntity.ok(workflow);
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in getWorkflow", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        }
    }

    // --------- Order Endpoints ---------
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@Valid @RequestBody Order order) throws JsonProcessingException {
        if (order == null || !order.isValid()) {
            logger.error("Invalid Order data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Order data"));
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Order", ENTITY_VERSION, order);
        UUID technicalId = idFuture.join();

        logger.info("Order created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Order", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Order not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
            }
            Order order = objectMapper.treeToValue(node, Order.class);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in getOrder", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        }
    }

    // --------- Customer Endpoints ---------
    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(@Valid @RequestBody Customer customer) throws JsonProcessingException {
        if (customer == null || !customer.isValid()) {
            logger.error("Invalid Customer data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Customer data"));
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Customer", ENTITY_VERSION, customer);
        UUID technicalId = idFuture.join();

        logger.info("Customer created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Customer", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Customer not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Customer not found"));
            }
            Customer customer = objectMapper.treeToValue(node, Customer.class);
            return ResponseEntity.ok(customer);
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in getCustomer", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        }
    }
}