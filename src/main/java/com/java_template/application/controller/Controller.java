package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Customer;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.Workflow;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    // --------- Workflow Endpoints ---------
    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (workflow == null || !workflow.isValid()) {
                logger.error("Invalid Workflow data received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Workflow data"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem("Workflow", ENTITY_VERSION, workflow);
            UUID technicalId = idFuture.join();

            logger.info("Workflow created with technicalId: {}", technicalId);

            processWorkflow(workflow);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in createWorkflow", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Error creating Workflow", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Workflow", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Workflow not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in getWorkflow", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception ex) {
            logger.error("Error retrieving Workflow", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --------- Order Endpoints ---------
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Order order) {
        try {
            if (order == null || !order.isValid()) {
                logger.error("Invalid Order data received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Order data"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem("Order", ENTITY_VERSION, order);
            UUID technicalId = idFuture.join();

            logger.info("Order created with technicalId: {}", technicalId);

            processOrder(order);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in createOrder", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Error creating Order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Order", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Order not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in getOrder", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception ex) {
            logger.error("Error retrieving Order", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --------- Customer Endpoints ---------
    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(@RequestBody Customer customer) {
        try {
            if (customer == null || !customer.isValid()) {
                logger.error("Invalid Customer data received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Customer data"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem("Customer", ENTITY_VERSION, customer);
            UUID technicalId = idFuture.join();

            logger.info("Customer created with technicalId: {}", technicalId);

            processCustomer(customer);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in createCustomer", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Error creating Customer", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Customer", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Customer not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Customer not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Illegal argument exception in getCustomer", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception ex) {
            logger.error("Error retrieving Customer", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --------- Process Methods ---------
    private void processWorkflow(Workflow workflow) {
        logger.info("Processing Workflow with name: {}", workflow.getName());

        // Validation: Check workflow configuration and prerequisites
        if (workflow.getName().isBlank() || workflow.getDescription().isBlank()) {
            logger.error("Workflow validation failed: name or description is blank");
            workflow.setStatus("FAILED");
            return;
        }
        workflow.setStatus("RUNNING");

        // Processing: Trigger orchestration logic (simulated)
        logger.info("Orchestrating related Orders and Customers for workflow: {}", workflow.getName());

        // Simulate completion
        workflow.setStatus("COMPLETED");
        logger.info("Workflow processing completed successfully");
    }

    private void processOrder(Order order) {
        logger.info("Processing Order with orderId: {}", order.getOrderId());

        // Validation: Check order details (product availability simulated)
        if (order.getQuantity() <= 0) {
            logger.error("Order validation failed: quantity must be greater than zero");
            order.setStatus("FAILED");
            return;
        }
        order.setStatus("PROCESSING");

        // Processing: Reserve inventory, initiate shipping process (simulated)
        logger.info("Reserving inventory for productCode: {}", order.getProductCode());

        // Simulate shipped status
        order.setStatus("SHIPPED");
        logger.info("Order processing completed successfully");
    }

    private void processCustomer(Customer customer) {
        logger.info("Processing Customer with customerId: {}", customer.getCustomerId());

        // Validation: Verify contact details format and uniqueness (basic simulation)
        if (customer.getEmail().isBlank() || customer.getPhone().isBlank()) {
            logger.error("Customer validation failed: email or phone is blank");
            return;
        }

        // Processing: Enrich customer profile (simulated)
        logger.info("Enriching customer profile for: {}", customer.getName());

        // Mark customer as ACTIVE
        logger.info("Customer processing completed successfully");
    }
}