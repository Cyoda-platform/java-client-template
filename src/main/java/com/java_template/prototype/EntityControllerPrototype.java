package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity as HttpResponseEntity;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, com.java_template.application.entity.PetRegistrationJob> petRegistrationJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petRegistrationJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // ----------- PetRegistrationJob endpoints -----------

    @PostMapping("/pet-registration-jobs")
    public ResponseEntity<Map<String, Object>> createPetRegistrationJob(@RequestBody com.java_template.application.entity.PetRegistrationJob job) {
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

        try {
            processPetRegistrationJob(job);
        } catch (Exception e) {
            log.error("Error processing PetRegistrationJob: {}", e.getMessage());
            job.setStatus("FAILED");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing failed"));
        }

        // Return job id and current pets
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId, "pets", petCache.values()));
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
        if (job.getPetName().isBlank() || job.getPetType().isBlank() || job.getOwnerName().isBlank()) {
            job.setStatus("FAILED");
            log.error("PetRegistrationJob validation failed for petName: {}", job.getPetName());
            return;
        }
        job.setStatus("PROCESSING");

        // Fetch pets from public API (Petstore Swagger)
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
        com.java_template.application.entity.Pet newPet;

        try {
            HttpResponseEntity<com.java_template.application.entity.Pet[]> response = restTemplate.getForEntity(url, com.java_template.application.entity.Pet[].class);
            com.java_template.application.entity.Pet[] petsFromApi = response.getBody();
            if (petsFromApi != null) {
                for (com.java_template.application.entity.Pet apiPet : petsFromApi) {
                    // Assign unique petId if not present
                    String petId = apiPet.getPetId();
                    if (petId == null || petId.isBlank()) {
                        petId = "pet-" + petIdCounter.getAndIncrement();
                        apiPet.setPetId(petId);
                    }
                    petCache.put(petId, apiPet);
                    log.info("Saved Pet from public API with ID: {}", petId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch pets from public API: {}", e.getMessage());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("COMPLETED");
        log.info("PetRegistrationJob processed successfully for petName: {}", job.getPetName());
    }

    // ----------- Pet endpoints -----------

    @GetMapping("/pets")
    public ResponseEntity<Collection<com.java_template.application.entity.Pet>> getPets() {
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
        if (pet.getName().isBlank() || pet.getCategory().isBlank() || pet.getStatus().isBlank()) {
            log.error("Pet validation failed for ID: {}", pet.getPetId());
            return;
        }
        log.info("Pet processed and available: {}", pet.getPetId());
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
        order.setStatus("APPROVED");
        log.info("Order approved for ID: {}", order.getOrderId());
    }
}
