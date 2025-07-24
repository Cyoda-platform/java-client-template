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
import com.java_template.application.entity.StoreOrder;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and Id counters for PurrfectPetsJob (orchestration entity)
    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    // Cache and Id counters for Pet (business entity)
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and Id counters for StoreOrder (business entity)
    private final ConcurrentHashMap<String, StoreOrder> storeOrderCache = new ConcurrentHashMap<>();
    private final AtomicLong storeOrderIdCounter = new AtomicLong(1);

    // ------------------ PurrfectPetsJob Endpoints ------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        if (job == null || !job.isValid()) {
            log.error("Invalid PurrfectPetsJob creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid job data"));
        }
        String technicalId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setTechnicalId(technicalId);
        job.setStatus("PENDING");
        job.setProcessedAt(null);
        purrfectPetsJobCache.put(technicalId, job);
        log.info("PurrfectPetsJob created with technicalId: {}", technicalId);

        processPurrfectPetsJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String technicalId) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(technicalId);
        if (job == null) {
            log.error("PurrfectPetsJob not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getTechnicalId());

        // Validate requestedStatus
        String status = job.getRequestedStatus();
        if (!List.of("available", "pending", "sold").contains(status)) {
            log.error("Invalid requestedStatus: {}", status);
            job.setStatus("FAILED");
            purrfectPetsJobCache.put(job.getTechnicalId(), job);
            return;
        }

        // Call external Petstore API to fetch pets by status (simulate)
        // For prototype, simulate retrieval of pets
        List<Pet> fetchedPets = fetchPetsFromPetstoreApi(status);

        // Persist pets as immutable entities (add to cache)
        for (Pet pet : fetchedPets) {
            String petKey = "pet-" + petIdCounter.getAndIncrement();
            petCache.put(petKey, pet);
            log.info("Persisted Pet with key: {}", petKey);
        }

        job.setProcessedAt(java.time.LocalDateTime.now());
        job.setStatus("COMPLETED");
        purrfectPetsJobCache.put(job.getTechnicalId(), job);
        log.info("Completed processing PurrfectPetsJob with ID: {}", job.getTechnicalId());
    }

    private List<Pet> fetchPetsFromPetstoreApi(String status) {
        // Simulate external API call: GET /pet/findByStatus?status={status}
        // Return sample pets for prototype purpose
        List<Pet> pets = new ArrayList<>();
        Pet samplePet = new Pet();
        samplePet.setPetId(100L);
        samplePet.setName("Whiskers");
        samplePet.setCategory("Cat");
        samplePet.setStatus(status);
        samplePet.setPhotoUrls(List.of("https://example.com/photos/whiskers1.jpg"));
        samplePet.setTags(List.of("cute", "playful"));
        pets.add(samplePet);
        return pets;
    }

    // ------------------ StoreOrder Endpoints ------------------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createStoreOrder(@RequestBody StoreOrder order) {
        if (order == null || !order.isValid()) {
            log.error("Invalid StoreOrder creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid order data"));
        }
        String technicalId = "order-" + storeOrderIdCounter.getAndIncrement();
        order.setOrderId(storeOrderIdCounter.getAndIncrement());
        order.setStatus("PENDING");
        if (order.getComplete() == null) {
            order.setComplete(false);
        }
        storeOrderCache.put(technicalId, order);
        log.info("StoreOrder created with technicalId: {}", technicalId);

        processStoreOrder(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/orders/{technicalId}")
    public ResponseEntity<?> getStoreOrder(@PathVariable String technicalId) {
        StoreOrder order = storeOrderCache.get(technicalId);
        if (order == null) {
            log.error("StoreOrder not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    private void processStoreOrder(StoreOrder order) {
        log.info("Processing StoreOrder with ID: {}", order.getOrderId());

        // Validate required fields
        if (order.getPetId() == null || order.getQuantity() == null || order.getQuantity() <= 0) {
            log.error("Invalid StoreOrder data: petId or quantity invalid");
            order.setStatus("FAILED");
            storeOrderCache.put("order-" + order.getOrderId(), order);
            return;
        }

        // Simulate sending order creation request to Petstore API /store/order
        boolean apiSuccess = simulatePetstoreOrderCreation(order);

        if (apiSuccess) {
            order.setStatus("COMPLETED");
            order.setComplete(true);
        } else {
            order.setStatus("FAILED");
            order.setComplete(false);
        }
        storeOrderCache.put("order-" + order.getOrderId(), order);
        log.info("Completed processing StoreOrder with ID: {}", order.getOrderId());
    }

    private boolean simulatePetstoreOrderCreation(StoreOrder order) {
        // Simulate API call success
        return true;
    }

    // ------------------ Pet Endpoints ------------------
    // Read-only GET by petId since pets are created by jobs

    @GetMapping("/pets/{petId}")
    public ResponseEntity<?> getPet(@PathVariable Long petId) {
        // Find pet in cache by matching petId field (not key)
        Optional<Pet> petOpt = petCache.values().stream()
                .filter(p -> petId.equals(p.getPetId()))
                .findFirst();
        if (petOpt.isEmpty()) {
            log.error("Pet not found for petId: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        }
        return ResponseEntity.ok(petOpt.get());
    }
}