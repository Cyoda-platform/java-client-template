package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Order;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and counters for PurrfectPetsJob entity
    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    // Cache and counters for Pet entity
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and counters for Order entity
    private final ConcurrentHashMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // POST /prototype/jobs - create a new PurrfectPetsJob
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody PurrfectPetsJob job) {
        if (job == null || !job.isValid()) {
            log.error("Invalid PurrfectPetsJob entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid job data"));
        }
        String technicalId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setStatus("PENDING");
        job.setCreatedAt(java.time.Instant.now().toString());
        purrfectPetsJobCache.put(technicalId, job);
        log.info("Created PurrfectPetsJob with technicalId: {}", technicalId);

        processPurrfectPetsJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/jobs/{id} - get PurrfectPetsJob by technicalId
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJobById(@PathVariable("id") String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            log.error("PurrfectPetsJob with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pets - create a new Pet entity
    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet data"));
        }
        String technicalId = "pet-" + petIdCounter.getAndIncrement();
        petCache.put(technicalId, pet);
        log.info("Created Pet with technicalId: {}", technicalId);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/pets/{id} - get Pet by technicalId
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPetById(@PathVariable("id") String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        }
        return ResponseEntity.ok(pet);
    }

    // Optional: GET /prototype/pets/findByStatus?status=available
    @GetMapping(value = "/pets/findByStatus")
    public ResponseEntity<List<Pet>> findPetsByStatus(@RequestParam("status") String status) {
        if (status == null || status.isBlank()) {
            log.error("Invalid status parameter for findByStatus");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
        }
        List<Pet> matchedPets = new ArrayList<>();
        for (Pet pet : petCache.values()) {
            if (pet.getStatus() != null && pet.getStatus().equalsIgnoreCase(status)) {
                matchedPets.add(pet);
            }
        }
        return ResponseEntity.ok(matchedPets);
    }

    // POST /prototype/orders - create a new Order entity
    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        if (order == null || !order.isValid()) {
            log.error("Invalid Order entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid order data"));
        }
        // Validate petId exists
        boolean petExists = petCache.values().stream()
                .anyMatch(pet -> pet.getPetId().equals(order.getPetId()));
        if (!petExists) {
            log.error("Order creation failed: petId {} does not exist", order.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet ID does not exist"));
        }
        String technicalId = "order-" + orderIdCounter.getAndIncrement();
        orderCache.put(technicalId, order);
        log.info("Created Order with technicalId: {}", technicalId);

        processOrder(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/orders/{id} - get Order by technicalId
    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable("id") String id) {
        Order order = orderCache.get(id);
        if (order == null) {
            log.error("Order with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    // PROCESS METHODS

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with jobName: {}", job.getJobName());
        // Validation already done

        try {
            // Simulate fetching pet data from Petstore API
            // For demo, just log and create a dummy pet entity
            Pet demoPet = new Pet();
            demoPet.setPetId(999L);
            demoPet.setName("DemoPet");
            demoPet.setCategory("cat");
            demoPet.setStatus("available");
            demoPet.setPhotoUrls("http://example.com/photo1.jpg");
            demoPet.setTags("demo,example");

            // Create new pet event (immutable)
            String petTechId = "pet-" + petIdCounter.getAndIncrement();
            petCache.put(petTechId, demoPet);
            log.info("Ingested Pet with technicalId: {}", petTechId);

            // Optionally create orders similarly if parameters request (skipped here)

            job.setStatus("COMPLETED");
            log.info("PurrfectPetsJob {} completed successfully", job.getJobName());
        } catch (Exception e) {
            log.error("Failed processing PurrfectPetsJob {}: {}", job.getJobName(), e.getMessage());
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with petId: {}", pet.getPetId());
        // Validate pet data status values against allowed: available, pending, sold
        List<String> allowedStatuses = Arrays.asList("available", "pending", "sold");
        if (!allowedStatuses.contains(pet.getStatus().toLowerCase())) {
            log.error("Invalid pet status: {}", pet.getStatus());
            // Possibly handle error or mark pet invalid in real impl
        }
        // Save as immutable event already done in POST
        log.info("Pet {} processed and stored", pet.getName());
    }

    private void processOrder(Order order) {
        log.info("Processing Order with orderId: {}, petId: {}", order.getOrderId(), order.getPetId());
        // Validate quantity > 0 already done in isValid
        // Save as immutable event already done in POST
        log.info("Order {} processed and stored", order.getOrderId());
    }
}