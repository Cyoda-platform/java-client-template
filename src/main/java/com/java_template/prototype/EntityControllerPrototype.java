package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PetUpdateJob;
import com.java_template.application.entity.Pet;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for each entity
    private final ConcurrentHashMap<String, PetUpdateJob> petUpdateJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petUpdateJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // ------------------- PetUpdateJob Endpoints -------------------

    @PostMapping("/petUpdateJob")
    public ResponseEntity<?> createPetUpdateJob(@RequestBody PetUpdateJob job) {
        if (job == null) {
            log.error("Received null PetUpdateJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetUpdateJob cannot be null");
        }
        // Generate IDs
        String newId = "PUJ-" + petUpdateJobIdCounter.getAndIncrement();
        job.setId(newId);
        job.setTechnicalId(UUID.randomUUID());

        // Validate required fields
        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            log.error("Invalid sourceUrl for PetUpdateJob {}", newId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required and cannot be blank");
        }
        if (job.getJobId() == null || job.getJobId().isBlank()) {
            // If jobId not provided, generate one
            job.setJobId(UUID.randomUUID().toString());
        }
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }

        petUpdateJobCache.put(newId, job);
        log.info("Created PetUpdateJob with ID: {}", newId);

        try {
            processPetUpdateJob(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(job);
        } catch (Exception e) {
            log.error("Error processing PetUpdateJob with ID: {}", newId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed: " + e.getMessage());
        }
    }

    @GetMapping("/petUpdateJob/{id}")
    public ResponseEntity<?> getPetUpdateJob(@PathVariable("id") String id) {
        PetUpdateJob job = petUpdateJobCache.get(id);
        if (job == null) {
            log.error("PetUpdateJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // ------------------- Pet Endpoints -------------------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Received null Pet");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet cannot be null");
        }

        String newId = "PET-" + petIdCounter.getAndIncrement();
        pet.setId(newId);
        pet.setTechnicalId(UUID.randomUUID());

        // Validate mandatory fields
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            log.error("Invalid petId for Pet {}", newId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required and cannot be blank");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Invalid name for Pet {}", newId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name is required and cannot be blank");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Invalid category for Pet {}", newId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("category is required and cannot be blank");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }

        petCache.put(newId, pet);
        log.info("Created Pet with ID: {}", newId);

        try {
            processPet(pet);
            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error processing Pet with ID: {}", newId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed: " + e.getMessage());
        }
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable("id") String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // ------------------- Process Methods -------------------

    private void processPetUpdateJob(PetUpdateJob job) {
        log.info("Processing PetUpdateJob with ID: {}", job.getId());

        // Validate sourceUrl format (basic check)
        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            log.error("PetUpdateJob {} has invalid sourceUrl", job.getId());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("PROCESSING");

        try {
            // Simulate fetching pets from external Petstore API URL
            // In real scenario, would do HTTP GET request and parse response

            // For demonstration, create a mock pet list
            Pet fetchedPet = new Pet();
            fetchedPet.setId("PET-" + petIdCounter.getAndIncrement());
            fetchedPet.setTechnicalId(UUID.randomUUID());
            fetchedPet.setPetId(UUID.randomUUID().toString());
            fetchedPet.setName("MockPet");
            fetchedPet.setCategory("cat");
            fetchedPet.setStatus("AVAILABLE");

            petCache.put(fetchedPet.getId(), fetchedPet);
            processPet(fetchedPet);

            job.setStatus("COMPLETED");
            log.info("PetUpdateJob {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Error processing PetUpdateJob {}: {}", job.getId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate mandatory fields (already validated on input, but double-check)
        if (pet.getPetId() == null || pet.getPetId().isBlank()
                || pet.getName() == null || pet.getName().isBlank()
                || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet {} failed validation during processing", pet.getId());
            throw new IllegalArgumentException("Pet validation failed: mandatory fields missing");
        }

        // Business logic: Mark pet as stored (in cache already)
        log.info("Pet {} stored successfully with status {}", pet.getId(), pet.getStatus());

        // Additional business logic could be added here, such as notifications or enrichment
    }
}