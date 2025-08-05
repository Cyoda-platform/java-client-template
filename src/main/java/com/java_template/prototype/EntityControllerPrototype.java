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

    // Workflow cache and ID counter
    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    // Pet cache and ID counter
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Order cache and ID counter
    private final ConcurrentHashMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // ----------- WORKFLOW ENDPOINTS -----------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (!workflow.isValid()) {
                log.error("Invalid Workflow entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String technicalId = "workflow-" + workflowIdCounter.getAndIncrement();
            workflowCache.put(technicalId, workflow);
            log.info("Workflow created with technicalId: {}", technicalId);
            processWorkflow(technicalId, workflow);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating Workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String technicalId) {
        Workflow workflow = workflowCache.get(technicalId);
        if (workflow == null) {
            log.error("Workflow not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(workflow);
    }

    private void processWorkflow(String technicalId, Workflow workflow) {
        // 1. Initial State: Workflow created with status = PENDING if not set
        if (workflow.getStatus() == null || workflow.getStatus().isBlank()) {
            workflow.setStatus("PENDING");
            log.info("Workflow {} initial status set to PENDING", technicalId);
        }

        // 2. Validation
        if (workflow.getName() == null || workflow.getName().isBlank()) {
            log.error("Workflow {} validation failed: name is blank", technicalId);
            workflow.setStatus("FAILED");
            return;
        }

        // 3. Orchestration: Example - trigger processing pets/orders if metadata indicates
        log.info("Processing Workflow orchestration for {}", technicalId);
        workflow.setStatus("RUNNING");

        // Simulate orchestration logic (e.g., triggering pet or order events)
        // For prototype, just log
        log.info("Workflow {} orchestration running", technicalId);

        // 4. Completion
        workflow.setStatus("COMPLETED");
        log.info("Workflow {} completed", technicalId);

        // 5. Notification or downstream events could be triggered here
    }

    // ----------- PET ENDPOINTS -----------

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        try {
            if (!pet.isValid()) {
                log.error("Invalid Pet entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String technicalId = "pet-" + petIdCounter.getAndIncrement();
            petCache.put(technicalId, pet);
            log.info("Pet created with technicalId: {}", technicalId);
            processPet(technicalId, pet);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<Pet> getPet(@PathVariable String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            log.error("Pet not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    private void processPet(String technicalId, Pet pet) {
        // 1. Initial State: Set default status if missing
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
            log.info("Pet {} initial status set to AVAILABLE", technicalId);
        }

        // 2. Validation of key fields already done in isValid()

        // 3. Processing: Simulate syncing/enriching pet info with Petstore API data
        log.info("Processing Pet {} syncing with Petstore API data", technicalId);
        // In real implementation, call Petstore API here

        // 4. Update status based on business rules (simulate adoption)
        if ("SOLD".equalsIgnoreCase(pet.getStatus())) {
            log.info("Pet {} marked SOLD", technicalId);
        } else if ("PENDING".equalsIgnoreCase(pet.getStatus())) {
            log.info("Pet {} marked PENDING", technicalId);
        }

        // 5. Completion: Finalize pet state
        log.info("Pet {} processing completed", technicalId);
    }

    // ----------- ORDER ENDPOINTS -----------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        try {
            if (!order.isValid()) {
                log.error("Invalid Order entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String technicalId = "order-" + orderIdCounter.getAndIncrement();
            orderCache.put(technicalId, order);
            log.info("Order created with technicalId: {}", technicalId);
            processOrder(technicalId, order);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/orders/{technicalId}")
    public ResponseEntity<Order> getOrder(@PathVariable String technicalId) {
        Order order = orderCache.get(technicalId);
        if (order == null) {
            log.error("Order not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(order);
    }

    private void processOrder(String technicalId, Order order) {
        // 1. Initial State: Set default status if missing
        if (order.getStatus() == null || order.getStatus().isBlank()) {
            order.setStatus("PLACED");
            log.info("Order {} initial status set to PLACED", technicalId);
        }

        // 2. Validation of key fields already done in isValid()

        // 3. Processing: Check pet availability and reserve pet (simulate)
        log.info("Processing Order {} for petId {}", technicalId, order.getPetId());
        Pet pet = petCache.values().stream()
                .filter(p -> p.getPetId() != null && p.getPetId().equals(order.getPetId()))
                .findFirst()
                .orElse(null);

        if (pet == null) {
            log.error("Pet {} not found for Order {}", order.getPetId(), technicalId);
            order.setStatus("FAILED");
            return;
        }

        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            log.error("Pet {} is not available for Order {}", pet.getPetId(), technicalId);
            order.setStatus("FAILED");
            return;
        }

        pet.setStatus("PENDING");
        log.info("Pet {} status set to PENDING due to Order {}", pet.getPetId(), technicalId);

        // 4. Shipping: Simulate setting shipDate if missing
        if (order.getShipDate() == null || order.getShipDate().isBlank()) {
            order.setShipDate(new Date().toString());
            log.info("Order {} shipDate set to current date", technicalId);
        }

        // 5. Completion: Mark order as APPROVED
        order.setStatus("APPROVED");
        log.info("Order {} status set to APPROVED", technicalId);

        // 6. Notification or downstream events could be triggered here
    }
}