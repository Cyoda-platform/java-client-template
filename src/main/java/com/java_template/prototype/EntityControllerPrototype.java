package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.Customer;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Customer> customerCache = new ConcurrentHashMap<>();
    private final AtomicLong customerIdCounter = new AtomicLong(1);

    // --------- Workflow Endpoints ---------
    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (workflow == null || !workflow.isValid()) {
            log.error("Invalid Workflow data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Workflow data"));
        }
        String technicalId = "wf-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);
        log.info("Workflow created with technicalId: {}", technicalId);

        processWorkflow(workflow);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        Workflow workflow = workflowCache.get(id);
        if (workflow == null) {
            log.error("Workflow not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
        }
        return ResponseEntity.ok(workflow);
    }

    // --------- Order Endpoints ---------
    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        if (order == null || !order.isValid()) {
            log.error("Invalid Order data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Order data"));
        }
        String technicalId = "or-" + orderIdCounter.getAndIncrement();
        orderCache.put(technicalId, order);
        log.info("Order created with technicalId: {}", technicalId);

        processOrder(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {
        Order order = orderCache.get(id);
        if (order == null) {
            log.error("Order not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    // --------- Customer Endpoints ---------
    @PostMapping("/customers")
    public ResponseEntity<Map<String, String>> createCustomer(@RequestBody Customer customer) {
        if (customer == null || !customer.isValid()) {
            log.error("Invalid Customer data received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Customer data"));
        }
        String technicalId = "cu-" + customerIdCounter.getAndIncrement();
        customerCache.put(technicalId, customer);
        log.info("Customer created with technicalId: {}", technicalId);

        processCustomer(customer);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable String id) {
        Customer customer = customerCache.get(id);
        if (customer == null) {
            log.error("Customer not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Customer not found"));
        }
        return ResponseEntity.ok(customer);
    }

    // --------- Process Methods ---------
    private void processWorkflow(Workflow workflow) {
        log.info("Processing Workflow with name: {}", workflow.getName());

        // Validation: Check workflow configuration and prerequisites
        if (workflow.getName().isBlank() || workflow.getDescription().isBlank()) {
            log.error("Workflow validation failed: name or description is blank");
            workflow.setStatus("FAILED");
            return;
        }
        workflow.setStatus("RUNNING");

        // Processing: Trigger orchestration logic (simulated)
        log.info("Orchestrating related Orders and Customers for workflow: {}", workflow.getName());

        // Simulate completion
        workflow.setStatus("COMPLETED");
        log.info("Workflow processing completed successfully");
    }

    private void processOrder(Order order) {
        log.info("Processing Order with orderId: {}", order.getOrderId());

        // Validation: Check order details (product availability simulated)
        if (order.getQuantity() <= 0) {
            log.error("Order validation failed: quantity must be greater than zero");
            order.setStatus("FAILED");
            return;
        }
        order.setStatus("PROCESSING");

        // Processing: Reserve inventory, initiate shipping process (simulated)
        log.info("Reserving inventory for productCode: {}", order.getProductCode());

        // Simulate shipped status
        order.setStatus("SHIPPED");
        log.info("Order processing completed successfully");
    }

    private void processCustomer(Customer customer) {
        log.info("Processing Customer with customerId: {}", customer.getCustomerId());

        // Validation: Verify contact details format and uniqueness (basic simulation)
        if (customer.getEmail().isBlank() || customer.getPhone().isBlank()) {
            log.error("Customer validation failed: email or phone is blank");
            return;
        }

        // Processing: Enrich customer profile (simulated)
        log.info("Enriching customer profile for: {}", customer.getName());

        // Mark customer as ACTIVE
        log.info("Customer processing completed successfully");
    }
}