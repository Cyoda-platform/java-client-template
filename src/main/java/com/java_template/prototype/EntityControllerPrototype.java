package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.PetOrder;
import com.java_template.application.entity.Pet;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Workflow cache and ID generator
    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    // PetOrder cache and ID generator
    private final ConcurrentHashMap<String, PetOrder> petOrderCache = new ConcurrentHashMap<>();
    private final AtomicLong petOrderIdCounter = new AtomicLong(1);

    // Pet cache and ID generator
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // --- Workflow endpoints ---

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (workflow == null || !workflow.isValid()) {
            log.error("Invalid Workflow creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid workflow data"));
        }
        String id = "workflow-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(id, workflow);
        log.info("Created Workflow with ID: {}", id);
        processWorkflow(workflow);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        Workflow workflow = workflowCache.get(id);
        if (workflow == null) {
            log.error("Workflow not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
        }
        return ResponseEntity.ok(workflow);
    }

    // --- PetOrder endpoints ---

    @PostMapping("/petOrders")
    public ResponseEntity<Map<String, String>> createPetOrder(@RequestBody PetOrder petOrder) {
        if (petOrder == null || !petOrder.isValid()) {
            log.error("Invalid PetOrder creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet order data"));
        }
        String id = "order-" + petOrderIdCounter.getAndIncrement();
        petOrderCache.put(id, petOrder);
        log.info("Created PetOrder with ID: {}", id);
        processPetOrder(petOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/petOrders/{id}")
    public ResponseEntity<?> getPetOrder(@PathVariable String id) {
        PetOrder order = petOrderCache.get(id);
        if (order == null) {
            log.error("PetOrder not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PetOrder not found"));
        }
        return ResponseEntity.ok(order);
    }

    // --- Pet endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet data"));
        }
        String id = "pet-" + petIdCounter.getAndIncrement();
        petCache.put(id, pet);
        log.info("Created Pet with ID: {}", id);
        processPet(pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        }
        return ResponseEntity.ok(pet);
    }

    // --- Process methods with business logic ---

    private void processWorkflow(Workflow workflow) {
        log.info("Processing Workflow with name: {}", workflow.getWorkflowName());
        // Validate and start orchestration
        if (workflow.getStatus().equalsIgnoreCase("CREATED")) {
            log.info("Starting orchestration for Workflow: {}", workflow.getWorkflowName());
            // Example: Ingest pet data and process orders (simulate)
            // On success:
            workflow.setStatus("COMPLETED");
            log.info("Workflow {} completed successfully", workflow.getWorkflowName());
        } else {
            log.error("Workflow {} is in invalid status: {}", workflow.getWorkflowName(), workflow.getStatus());
            workflow.setStatus("FAILED");
        }
    }

    private void processPetOrder(PetOrder petOrder) {
        log.info("Processing PetOrder for petId: {}", petOrder.getPetId());
        // Validate pet availability (simulate by checking petCache for petId with AVAILABLE status)
        boolean petAvailable = petCache.values().stream()
                .anyMatch(p -> p.getPetId().equals(petOrder.getPetId()) && "AVAILABLE".equalsIgnoreCase(p.getStatus()));

        if (!petAvailable) {
            log.error("Pet with ID {} is not available for order", petOrder.getPetId());
            petOrder.setStatus("CANCELLED");
            return;
        }
        // Simulate placing order to Petstore API and on success:
        petOrder.setStatus("APPROVED");
        log.info("PetOrder approved for petId: {}", petOrder.getPetId());
        // Additional: Could trigger notification here
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getPetId());
        // Validate pet data fields are correct (already validated by isValid)
        // Save or update local cache - already done on creation
        // Simulate status changes or enrichment if needed
        if (pet.getStatus().equalsIgnoreCase("AVAILABLE") || pet.getStatus().equalsIgnoreCase("PENDING") || pet.getStatus().equalsIgnoreCase("SOLD")) {
            log.info("Pet {} status is valid: {}", pet.getName(), pet.getStatus());
        } else {
            log.error("Pet {} has invalid status: {}", pet.getName(), pet.getStatus());
            pet.setStatus("AVAILABLE"); // default fallback
        }
        // Notify listeners or trigger events if needed
    }
}