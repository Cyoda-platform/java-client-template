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

    // Workflow caches and ID counter
    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    // Pet caches and ID counter
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Order caches and ID counter
    private final ConcurrentHashMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // ------------- Workflow Endpoints --------------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (!workflow.isValid()) {
            log.error("Invalid Workflow entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(workflowIdCounter.getAndIncrement());
        workflowCache.put(technicalId, workflow);
        log.info("Workflow created with technicalId: {}", technicalId);
        processWorkflow(technicalId, workflow);
        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId));
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") String id) {
        Workflow workflow = workflowCache.get(id);
        if (workflow == null) {
            log.error("Workflow not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(workflow);
    }

    // ------------- Pet Endpoints --------------

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        if (!pet.isValid()) {
            log.error("Invalid Pet entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(petIdCounter.getAndIncrement());
        petCache.put(technicalId, pet);
        log.info("Pet created with technicalId: {}", technicalId);
        processPet(technicalId, pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId));
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    // Optional GET by status for Pets
    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPetsByStatus(@RequestParam(value = "status", required = false) String status) {
        if (status == null) {
            return ResponseEntity.ok(new ArrayList<>(petCache.values()));
        }
        List<Pet> filtered = new ArrayList<>();
        for (Pet pet : petCache.values()) {
            if (pet.getStatus() != null && pet.getStatus().equalsIgnoreCase(status)) {
                filtered.add(pet);
            }
        }
        return ResponseEntity.ok(filtered);
    }

    // ------------- Order Endpoints --------------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        if (!order.isValid()) {
            log.error("Invalid Order entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // Validate petId exists
        if (!petCache.containsKey(order.getPetId())) {
            log.error("Order creation failed: PetId {} does not exist", order.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("error", "Invalid petId"));
        }

        String technicalId = String.valueOf(orderIdCounter.getAndIncrement());
        orderCache.put(technicalId, order);
        log.info("Order created with technicalId: {}", technicalId);
        processOrder(technicalId, order);
        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable("id") String id) {
        Order order = orderCache.get(id);
        if (order == null) {
            log.error("Order not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(order);
    }

    // Optional GET by status for Orders
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrdersByStatus(@RequestParam(value = "status", required = false) String status) {
        if (status == null) {
            return ResponseEntity.ok(new ArrayList<>(orderCache.values()));
        }
        List<Order> filtered = new ArrayList<>();
        for (Order order : orderCache.values()) {
            if (order.getStatus() != null && order.getStatus().equalsIgnoreCase(status)) {
                filtered.add(order);
            }
        }
        return ResponseEntity.ok(filtered);
    }

    // -------- Process Methods -----------

    private void processWorkflow(String technicalId, Workflow workflow) {
        // 1. Validate workflow parameters
        if (workflow.getName().isBlank()) {
            log.error("Workflow {} has blank name", technicalId);
            return;
        }
        if (workflow.getStatus().isBlank()) {
            log.error("Workflow {} has blank status", technicalId);
            return;
        }
        log.info("Processing Workflow id {} with status {}", technicalId, workflow.getStatus());
        // 2. Trigger orchestrated entity creations example: could trigger pets or orders (simulated here)
        // 3. Monitor and update status as needed (not implemented in prototype)
        // 4. Log completion
        log.info("Workflow {} processing completed", technicalId);
    }

    private void processPet(String technicalId, Pet pet) {
        // Validate pet name and category
        if (pet.getName().isBlank() || pet.getCategory().isBlank()) {
            log.error("Pet {} validation failed: name or category blank", technicalId);
            return;
        }
        // Assign default tags if empty
        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        if (!pet.getTags().contains("fun")) {
            pet.getTags().add("fun");
        }
        // Assign default photoUrls if empty
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new ArrayList<>());
        }
        if (pet.getPhotoUrls().isEmpty()) {
            pet.getPhotoUrls().add("https://example.com/default-pet-photo.jpg");
        }
        log.info("Processed Pet id {} with name {}", technicalId, pet.getName());
        // Notify inventory system or other services here (not implemented)
    }

    private void processOrder(String technicalId, Order order) {
        // Check quantity positive
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            log.error("Order {} validation failed: quantity invalid", technicalId);
            return;
        }
        // Check petId exists - already done in createOrder
        // Calculate ship date if missing - here just log it
        if (order.getShipDate() == null || order.getShipDate().isBlank()) {
            log.info("Order {} has no shipDate set, defaulting to today", technicalId);
            order.setShipDate(java.time.LocalDate.now().toString());
        }
        // Approve order by default
        order.setStatus("approved");
        order.setComplete(Boolean.FALSE);
        log.info("Processed Order id {} for petId {} with quantity {}", technicalId, order.getPetId(), order.getQuantity());
        // Trigger shipment or billing workflows here (not implemented)
    }
}