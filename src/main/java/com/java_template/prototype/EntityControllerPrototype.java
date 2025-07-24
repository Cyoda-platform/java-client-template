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

    // ----------- Workflow Endpoints -----------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (workflow == null || !workflow.isValid()) {
            log.error("Invalid Workflow entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid workflow data"));
        }
        String technicalId = "wf-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);
        log.info("Created Workflow with technicalId: {}", technicalId);
        processWorkflow(workflow);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable("id") String id) {
        Workflow workflow = workflowCache.get(id);
        if (workflow == null) {
            log.error("Workflow not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Workflow not found"));
        }
        return ResponseEntity.ok(workflow);
    }

    private void processWorkflow(Workflow workflow) {
        log.info("Processing Workflow with name: {}", workflow.getName());
        // Validation
        if (workflow.getParameters() == null || workflow.getParameters().isEmpty()) {
            log.error("Workflow parameters are missing");
            return;
        }
        // Set status RUNNING
        workflow.setStatus("RUNNING");
        // Example orchestration: Create Order entity if orderId param exists
        Object orderIdObj = workflow.getParameters().get("orderId");
        if (orderIdObj instanceof String) {
            String orderId = (String) orderIdObj;
            Order order = new Order();
            order.setOrderId(orderId);
            order.setCustomerId("unknown"); // default or from parameters
            order.setItems(new ArrayList<>());
            order.setShippingAddress("");
            order.setPaymentMethod("");
            order.setCreatedAt(new Date().toInstant().toString());
            order.setStatus("CREATED");
            String technicalOrderId = "ord-" + orderIdCounter.getAndIncrement();
            orderCache.put(technicalOrderId, order);
            log.info("Workflow created Order with technicalId: {}", technicalOrderId);
            processOrder(order);
        }
        // Mark completion
        workflow.setStatus("COMPLETED");
        log.info("Workflow processing completed for: {}", workflow.getName());
    }

    // ----------- Order Endpoints -----------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        if (order == null || !order.isValid()) {
            log.error("Invalid Order entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid order data"));
        }
        String technicalId = "ord-" + orderIdCounter.getAndIncrement();
        orderCache.put(technicalId, order);
        log.info("Created Order with technicalId: {}", technicalId);
        processOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable("id") String id) {
        Order order = orderCache.get(id);
        if (order == null) {
            log.error("Order not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    private void processOrder(Order order) {
        log.info("Processing Order with orderId: {}", order.getOrderId());
        // Validate customer existence (mock check)
        if (order.getCustomerId() == null || order.getCustomerId().isBlank()) {
            log.error("Order customerId is invalid");
            return;
        }
        // Validate items presence
        if (order.getItems() == null || order.getItems().isEmpty()) {
            log.error("Order items are empty");
            return;
        }
        // Simulate stock reservation and payment processing
        boolean paymentSuccess = true; // simulate success
        if (paymentSuccess) {
            order.setStatus("PAID");
            log.info("Payment successful for orderId: {}", order.getOrderId());
            // Simulate shipment initiation
            order.setStatus("SHIPPED");
            log.info("Shipment initiated for orderId: {}", order.getOrderId());
        } else {
            order.setStatus("FAILED");
            log.error("Payment failed for orderId: {}", order.getOrderId());
        }
    }

    // ----------- Customer Endpoints -----------

    @PostMapping("/customers")
    public ResponseEntity<Map<String, String>> createCustomer(@RequestBody Customer customer) {
        if (customer == null || !customer.isValid()) {
            log.error("Invalid Customer entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid customer data"));
        }
        String technicalId = "cust-" + customerIdCounter.getAndIncrement();
        customerCache.put(technicalId, customer);
        log.info("Created Customer with technicalId: {}", technicalId);
        processCustomer(customer);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable("id") String id) {
        Customer customer = customerCache.get(id);
        if (customer == null) {
            log.error("Customer not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Customer not found"));
        }
        return ResponseEntity.ok(customer);
    }

    private void processCustomer(Customer customer) {
        log.info("Processing Customer with customerId: {}", customer.getCustomerId());
        // Validate email format (simple check)
        if (!customer.getEmail().contains("@")) {
            log.error("Customer email format invalid: {}", customer.getEmail());
            return;
        }
        // Enrich profile: For example, trim name and phone
        customer.setName(customer.getName().trim());
        customer.setPhone(customer.getPhone().trim());
        log.info("Customer profile enriched for customerId: {}", customer.getCustomerId());
    }
}