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

    // Cache and Id counters for PetRegistrationJob entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.PetRegistrationJob> petRegistrationJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petRegistrationJobIdCounter = new AtomicLong(1);

    // Cache and Id counters for Pet entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and Id counters for Order entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // ----------- PetRegistrationJob endpoints -----------

    @PostMapping("/pet-registration-jobs")
    public ResponseEntity<?> createPetRegistrationJob(@RequestBody com.java_template.application.entity.PetRegistrationJob job) {
        if (job == null || job.getPetName() == null || job.getPetName().isBlank()
                || job.getPetType() == null || job.getPetType().isBlank()
                || job.getPetStatus() == null || job.getPetStatus().isBlank()
                || job.getOwnerName() == null || job.getOwnerName().isBlank()) {
            log.error("Invalid PetRegistrationJob input");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Missing required fields"));
        }

        String technicalId = "prj-" + petRegistrationJobIdCounter.getAndIncrement();
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());
        petRegistrationJobCache.put(technicalId, job);

        log.info("Created PetRegistrationJob with ID: {}", technicalId);

        processPetRegistrationJob(job);

        // Return current pets list from public pets API simulation
        Collection<com.java_template.application.entity.Pet> pets = petCache.values();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId, "pets", pets));
    }

    @GetMapping("/pet-registration-jobs/{id}")
    public ResponseEntity<com.java_template.application.entity.PetRegistrationJob> getPetRegistrationJob(@PathVariable String id) {
        var job = petRegistrationJobCache.get(id);
        if (job == null) {
            log.error("PetRegistrationJob not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    private void processPetRegistrationJob(com.java_template.application.entity.PetRegistrationJob job) {
        log.info("Processing PetRegistrationJob with petName: {}", job.getPetName());
        // Validation
        if (job.getPetName().isBlank() || job.getPetType().isBlank() || job.getOwnerName().isBlank()) {
            job.setStatus("FAILED");
            log.error("PetRegistrationJob validation failed for petName: {}", job.getPetName());
            return;
        }
        job.setStatus("PROCESSING");

        // Implement logic to fetch data from public pets API and save them as pets
        // Simulated here by creating a new Pet entity from job data and adding to cache
        String petTechnicalId = "pet-" + petIdCounter.getAndIncrement();
        com.java_template.application.entity.Pet pet = new com.java_template.application.entity.Pet();
        pet.setPetId(petTechnicalId);
        pet.setName(job.getPetName());
        pet.setCategory(job.getPetType());
        pet.setStatus(job.getPetStatus());
        pet.setPhotoUrls(Collections.emptyList());
        pet.setTags(Collections.emptyList());
        petCache.put(petTechnicalId, pet);

        log.info("Fetched and saved pet from public API simulation with ID: {}", petTechnicalId);

        job.setStatus("COMPLETED");
        log.info("PetRegistrationJob processing completed for petName: {}", job.getPetName());
    }

    // ----------- Pet endpoints -----------

    // Removed POST /pets endpoint as pet creation should happen via job submission

    @GetMapping("/pets")
    public ResponseEntity<Collection<com.java_template.application.entity.Pet>> getAllPets() {
        return ResponseEntity.ok(petCache.values());
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<com.java_template.application.entity.Pet> getPet(@PathVariable String id) {
        var pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    private void processPet(com.java_template.application.entity.Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getPetId());
        // Validate required fields
        if (pet.getName().isBlank() || pet.getCategory().isBlank() || pet.getStatus().isBlank()) {
            log.error("Pet validation failed for ID: {}", pet.getPetId());
            return;
        }
        // No additional processing for now per requirements
        log.info("Pet processed and marked as available: {}", pet.getPetId());
    }

    // ----------- Order endpoints -----------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody com.java_template.application.entity.Order order) {
        if (order == null || order.getOrderId() == null || order.getOrderId().isBlank()
                || order.getPetId() == null || order.getPetId().isBlank()
                || order.getQuantity() == null || order.getQuantity() <= 0
                || order.getStatus() == null || order.getStatus().isBlank()) {
            log.error("Invalid Order input");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Missing or invalid required fields"));
        }

        orderCache.put(order.getOrderId(), order);
        log.info("Created Order with ID: {}", order.getOrderId());

        processOrder(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", order.getOrderId()));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<com.java_template.application.entity.Order> getOrder(@PathVariable String id) {
        var order = orderCache.get(id);
        if (order == null) {
            log.error("Order not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(order);
    }

    private void processOrder(com.java_template.application.entity.Order order) {
        log.info("Processing Order with ID: {}", order.getOrderId());
        com.java_template.application.entity.Pet pet = petCache.get(order.getPetId());
        if (pet == null) {
            log.error("Order processing failed: Pet not found with ID: {}", order.getPetId());
            order.setStatus("FAILED");
            return;
        }
        if (!pet.getStatus().equalsIgnoreCase("AVAILABLE")) {
            log.error("Order processing failed: Pet not available with ID: {}", order.getPetId());
            order.setStatus("FAILED");
            return;
        }
        // For simplicity, assume stock is always sufficient
        order.setStatus("APPROVED");
        log.info("Order approved for ID: {}", order.getOrderId());
        // Notification or shipment logic could be added here
    }
}
