package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Order;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Workflow caches and counters
    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    // Pet caches and counters
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Order caches and counters
    private final ConcurrentHashMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // --- Workflow endpoints ---

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (workflow == null) {
            log.error("Workflow creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Workflow data is required"));
        }
        if (workflow.getName() == null || workflow.getName().isBlank()) {
            log.error("Workflow creation failed: name is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Workflow name is required"));
        }
        if (workflow.getPetStoreApiUrl() == null || workflow.getPetStoreApiUrl().isBlank()) {
            log.error("Workflow creation failed: petStoreApiUrl is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "PetStoreApiUrl is required"));
        }
        String technicalId = "workflow-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);
        log.info("Created Workflow with technicalId: {}", technicalId);
        processWorkflow(workflow);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<?> getWorkflow(@PathVariable String technicalId) {
        Workflow workflow = workflowCache.get(technicalId);
        if (workflow == null) {
            log.error("Workflow not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
        }
        return ResponseEntity.ok(workflow);
    }

    // --- Pet endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Pet creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet data is required"));
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: name is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet name is required"));
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet creation failed: category is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet category is required"));
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet creation failed: status is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet status is required"));
        }
        String technicalId = "pet-" + petIdCounter.getAndIncrement();
        petCache.put(technicalId, pet);
        log.info("Created Pet with technicalId: {}", technicalId);
        processPet(pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            log.error("Pet not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        }
        return ResponseEntity.ok(pet);
    }

    // --- Order endpoints ---

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        if (order == null) {
            log.error("Order creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Order data is required"));
        }
        if (order.getPetId() == null) {
            log.error("Order creation failed: petId is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "PetId is required"));
        }
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            log.error("Order creation failed: quantity invalid");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Quantity must be positive"));
        }
        if (order.getStatus() == null || order.getStatus().isBlank()) {
            log.error("Order creation failed: status is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Order status is required"));
        }
        String technicalId = "order-" + orderIdCounter.getAndIncrement();
        orderCache.put(technicalId, order);
        log.info("Created Order with technicalId: {}", technicalId);
        processOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/orders/{technicalId}")
    public ResponseEntity<?> getOrder(@PathVariable String technicalId) {
        Order order = orderCache.get(technicalId);
        if (order == null) {
            log.error("Order not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    // --- Process methods implementing business logic ---

    private void processWorkflow(Workflow workflow) {
        log.info("Processing Workflow with name: {}", workflow.getName());
        // Validate workflow parameters and Petstore API connectivity
        // For example, check petStoreApiUrl format
        if (workflow.getPetStoreApiUrl() == null || workflow.getPetStoreApiUrl().isBlank()) {
            log.error("Invalid PetStore API URL");
            workflow.setStatus("FAILED");
            return;
        }
        workflow.setStatus("PROCESSING");
        // Simulate ingestion of pets and orders from Petstore API
        // This is a prototype - real API calls omitted
        log.info("Fetching pets and orders from Petstore API: {}", workflow.getPetStoreApiUrl());
        // Simulate success
        workflow.setStatus("COMPLETED");
        log.info("Workflow '{}' completed successfully", workflow.getName());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with name: {}", pet.getName());
        // Validate pet data fields
        if (pet.getName().isBlank() || pet.getCategory().isBlank() || pet.getStatus().isBlank()) {
            log.error("Pet data validation failed");
            return;
        }
        // Save pet data immutably; enrich or tag pet if needed - simulated here
        log.info("Pet '{}' processed successfully", pet.getName());
    }

    private void processOrder(Order order) {
        log.info("Processing Order for petId: {}", order.getPetId());
        // Validate order fields and pet availability - simulated here
        if (order.getQuantity() <= 0) {
            log.error("Invalid order quantity");
            return;
        }
        // Process order - simulate status update
        log.info("Order processed with status: {}", order.getStatus());
    }
}