package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, ProductUploadJob> productUploadJobCache = new ConcurrentHashMap<>();
    private final AtomicLong productUploadJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, CustomerProfileUpdate> customerProfileUpdateCache = new ConcurrentHashMap<>();
    private final AtomicLong customerProfileUpdateIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // POST /prototype/productuploadjob
    @PostMapping("/productuploadjob")
    public ResponseEntity<String> createProductUploadJob(@RequestBody ProductUploadJob entity) {
        if (entity == null || !entity.isValid()) {
            log.error("Invalid ProductUploadJob entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        String technicalId = "PUJ-" + productUploadJobIdCounter.getAndIncrement();
        productUploadJobCache.put(technicalId, entity);
        try {
            processProductUploadJob(technicalId, entity);
            log.info("Processed ProductUploadJob with id {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing ProductUploadJob with id " + technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(technicalId);
    }

    // GET /prototype/productuploadjob/{id}
    @GetMapping("/productuploadjob/{id}")
    public ResponseEntity<ProductUploadJob> getProductUploadJob(@PathVariable("id") String technicalId) {
        ProductUploadJob entity = productUploadJobCache.get(technicalId);
        if (entity == null) {
            log.error("ProductUploadJob not found with id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(entity);
    }

    // POST /prototype/customerprofileupdate
    @PostMapping("/customerprofileupdate")
    public ResponseEntity<String> createCustomerProfileUpdate(@RequestBody CustomerProfileUpdate entity) {
        if (entity == null || !entity.isValid()) {
            log.error("Invalid CustomerProfileUpdate entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        String technicalId = "CPU-" + customerProfileUpdateIdCounter.getAndIncrement();
        customerProfileUpdateCache.put(technicalId, entity);
        try {
            processCustomerProfileUpdate(technicalId, entity);
            log.info("Processed CustomerProfileUpdate with id {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing CustomerProfileUpdate with id " + technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(technicalId);
    }

    // GET /prototype/customerprofileupdate/{id}
    @GetMapping("/customerprofileupdate/{id}")
    public ResponseEntity<CustomerProfileUpdate> getCustomerProfileUpdate(@PathVariable("id") String technicalId) {
        CustomerProfileUpdate entity = customerProfileUpdateCache.get(technicalId);
        if (entity == null) {
            log.error("CustomerProfileUpdate not found with id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(entity);
    }

    // POST /prototype/order
    @PostMapping("/order")
    public ResponseEntity<String> createOrder(@RequestBody Order entity) {
        if (entity == null || !entity.isValid()) {
            log.error("Invalid Order entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        String technicalId = "ORD-" + orderIdCounter.getAndIncrement();
        orderCache.put(technicalId, entity);
        try {
            processOrder(technicalId, entity);
            log.info("Processed Order with id {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing Order with id " + technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(technicalId);
    }

    // GET /prototype/order/{id}
    @GetMapping("/order/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable("id") String technicalId) {
        Order entity = orderCache.get(technicalId);
        if (entity == null) {
            log.error("Order not found with id {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(entity);
    }

    // Business logic implementations

    private void processProductUploadJob(String technicalId, ProductUploadJob entity) {
        log.info("Starting processing ProductUploadJob: {}", technicalId);
        // 1. Validate CSV format (simplified check)
        if (entity.getCsvData().isBlank()) {
            log.error("CSV data is blank for ProductUploadJob: {}", technicalId);
            entity.setStatus("FAILED");
            return;
        }
        entity.setStatus("PROCESSING");
        log.info("Parsing CSV for ProductUploadJob: {}", technicalId);
        // 2. Simulate parsing CSV and creating Product entities
        // In real implementation, parse CSV and create Product entities
        // 3. Update status to COMPLETED
        entity.setStatus("COMPLETED");
        log.info("Completed processing ProductUploadJob: {}", technicalId);
    }

    private void processCustomerProfileUpdate(String technicalId, CustomerProfileUpdate entity) {
        log.info("Processing CustomerProfileUpdate: {}", technicalId);
        // Validate updated fields
        if (entity.getUpdatedFields() == null || entity.getUpdatedFields().isEmpty()) {
            log.error("Updated fields are empty for CustomerProfileUpdate: {}", technicalId);
            return;
        }
        // Apply changes as immutable event (logging here)
        log.info("Applied profile updates for customerId: {}", entity.getCustomerId());
        // Confirmation logic can be added here if needed
    }

    private void processOrder(String technicalId, Order entity) {
        log.info("Processing Order: {}", technicalId);
        // Validate stock availability (simplified, assume always available)
        boolean stockAvailable = true;
        if (!stockAvailable) {
            log.error("Stock not available for Order: {}", technicalId);
            entity.setStatus("FAILED");
            return;
        }
        // Deduct stock (not implemented here)
        // Calculate totals already assumed valid in isValid
        entity.setStatus("CONFIRMED");
        log.info("Order confirmed: {}", technicalId);
        // Notify customer, update inventory, etc.
    }
}