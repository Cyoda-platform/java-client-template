package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID generator for Pet entities
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and ID generator for PetOrder entities
    private final ConcurrentHashMap<String, PetOrder> petOrderCache = new ConcurrentHashMap<>();
    private final AtomicLong petOrderIdCounter = new AtomicLong(1);

    // POST /prototype/pets - Create new Pet
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet petRequest) {
        if (petRequest == null) {
            log.error("Pet creation failed: Request body is null");
            return ResponseEntity.badRequest().body("Pet data is required");
        }
        if (petRequest.getName() == null || petRequest.getName().isBlank()) {
            log.error("Pet creation failed: Name is blank");
            return ResponseEntity.badRequest().body("Pet name is required");
        }
        if (petRequest.getType() == null || petRequest.getType().isBlank()) {
            log.error("Pet creation failed: Type is blank");
            return ResponseEntity.badRequest().body("Pet type is required");
        }

        String newId = "pet-" + petIdCounter.getAndIncrement();
        petRequest.setPetId(newId);
        petRequest.setStatus("AVAILABLE");
        petCache.put(newId, petRequest);

        processPet(petRequest);

        log.info("Created Pet with ID: {}", newId);
        return new ResponseEntity<>(petRequest, HttpStatus.CREATED);
    }

    // GET /prototype/pets/{petId} - Retrieve Pet by ID
    @GetMapping("/pets/{petId}")
    public ResponseEntity<?> getPet(@PathVariable String petId) {
        Pet pet = petCache.get(petId);
        if (pet == null) {
            log.error("Pet not found with ID: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/orders - Create new PetOrder
    @PostMapping("/orders")
    public ResponseEntity<?> createPetOrder(@RequestBody PetOrder orderRequest) {
        if (orderRequest == null) {
            log.error("Order creation failed: Request body is null");
            return ResponseEntity.badRequest().body("Order data is required");
        }
        if (orderRequest.getCustomerName() == null || orderRequest.getCustomerName().isBlank()) {
            log.error("Order creation failed: Customer name is blank");
            return ResponseEntity.badRequest().body("Customer name is required");
        }
        if (orderRequest.getPetId() == null || orderRequest.getPetId().isBlank()) {
            log.error("Order creation failed: Pet ID is blank");
            return ResponseEntity.badRequest().body("Pet ID is required");
        }
        if (orderRequest.getQuantity() == null || orderRequest.getQuantity() < 1) {
            log.error("Order creation failed: Quantity invalid");
            return ResponseEntity.badRequest().body("Quantity must be at least 1");
        }

        String newOrderId = "order-" + petOrderIdCounter.getAndIncrement();
        orderRequest.setOrderId(newOrderId);
        orderRequest.setStatus("PENDING");
        petOrderCache.put(newOrderId, orderRequest);

        processPetOrder(orderRequest);

        log.info("Created PetOrder with ID: {}", newOrderId);
        return new ResponseEntity<>(orderRequest, HttpStatus.CREATED);
    }

    // GET /prototype/orders/{orderId} - Retrieve PetOrder by ID
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getPetOrder(@PathVariable String orderId) {
        PetOrder order = petOrderCache.get(orderId);
        if (order == null) {
            log.error("Order not found with ID: {}", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
        }
        return ResponseEntity.ok(order);
    }

    // Business logic for processing Pet entity
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getPetId());
        // Validate pet data completeness
        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getType() == null || pet.getType().isBlank()) {
            log.error("Pet validation failed for ID: {}", pet.getPetId());
            return;
        }
        // Pet status is already set to AVAILABLE on creation
        // Emit event (simulated by log here)
        log.info("Pet {} is available for orders", pet.getPetId());
    }

    // Business logic for processing PetOrder entity
    private void processPetOrder(PetOrder order) {
        log.info("Processing PetOrder with ID: {}", order.getOrderId());
        // Validate order data and pet availability
        Pet pet = petCache.get(order.getPetId());
        if (pet == null) {
            log.error("Order failed: Pet not found with ID: {}", order.getPetId());
            order.setStatus("FAILED");
            return;
        }
        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            log.error("Order failed: Pet with ID {} not available", pet.getPetId());
            order.setStatus("FAILED");
            return;
        }
        if (order.getQuantity() == null || order.getQuantity() < 1) {
            log.error("Order failed: Invalid quantity for order ID: {}", order.getOrderId());
            order.setStatus("FAILED");
            return;
        }

        // Reserve pet by marking it SOLD
        pet.setStatus("SOLD");
        petCache.put(pet.getPetId(), pet);

        // Update order status to COMPLETED
        order.setStatus("COMPLETED");
        petOrderCache.put(order.getOrderId(), order);

        // Emit event (simulated by log here)
        log.info("Order {} completed successfully for pet ID {}", order.getOrderId(), order.getPetId());
    }

    // Entity classes as inner static classes for prototype purposes
    public static class Pet {
        private String petId;
        private String name;
        private String type;
        private String status;

        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PetOrder {
        private String orderId;
        private String petId;
        private String customerName;
        private Integer quantity;
        private String status;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}