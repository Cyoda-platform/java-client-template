package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counters for PurrfectPetsJob entity
    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    // Cache and ID counters for Pet entity
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // ----------------- PurrfectPetsJob Endpoints -----------------

    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        try {
            // Basic validation of required fields
            if (job.getActionType() == null || job.getActionType().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or blank actionType");
            }

            // Generate IDs
            String id = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
            job.setId(id);
            job.setJobId(id);
            job.setStatus("PENDING");
            job.setCreatedAt(java.time.LocalDateTime.now());
            job.setTechnicalId(UUID.randomUUID());

            if (!job.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Job entity validation failed");
            }

            purrfectPetsJobCache.put(id, job);

            processPurrfectPetsJob(job);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", id, "status", job.getStatus()));
        } catch (Exception e) {
            log.error("Failed to create PurrfectPetsJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create job");
        }
    }

    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // ----------------- Pet Endpoints -----------------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            // Validate required fields (partial check)
            if (pet.getName() == null || pet.getName().isBlank() ||
                pet.getType() == null || pet.getType().isBlank() ||
                pet.getBreed() == null || pet.getBreed().isBlank() ||
                pet.getAvailabilityStatus() == null || pet.getAvailabilityStatus().isBlank() ||
                pet.getStatus() == null || pet.getStatus().isBlank() ||
                pet.getAge() == null || pet.getAge() < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid pet fields");
            }

            // Generate IDs
            String id = "pet-" + petIdCounter.getAndIncrement();
            pet.setId(id);
            pet.setPetId(id);
            pet.setTechnicalId(UUID.randomUUID());

            if (!pet.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet entity validation failed");
            }

            petCache.put(id, pet);

            processPet(pet);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("petId", id, "status", pet.getStatus()));
        } catch (Exception e) {
            log.error("Failed to create Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create pet");
        }
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // ----------------- Process Methods -----------------

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());

        // Validation of actionType
        String action = job.getActionType().toUpperCase(Locale.ROOT).trim();
        if (!action.equals("FETCH_PETS") && !action.equals("UPDATE_PET_STATUS")) {
            log.error("Invalid actionType: {}", job.getActionType());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("PROCESSING");

        try {
            if (action.equals("FETCH_PETS")) {
                // Simulate fetching pets from Petstore API and creating Pet entities
                // Here we simulate with dummy data for prototype

                // Example fetched pets data
                List<Pet> fetchedPets = new ArrayList<>();
                Pet samplePet1 = new Pet();
                samplePet1.setId("pet-" + petIdCounter.getAndIncrement());
                samplePet1.setPetId(samplePet1.getId());
                samplePet1.setTechnicalId(UUID.randomUUID());
                samplePet1.setName("Simba");
                samplePet1.setType("cat");
                samplePet1.setBreed("Siamese");
                samplePet1.setAge(3);
                samplePet1.setAvailabilityStatus("AVAILABLE");
                samplePet1.setStatus("NEW");

                Pet samplePet2 = new Pet();
                samplePet2.setId("pet-" + petIdCounter.getAndIncrement());
                samplePet2.setPetId(samplePet2.getId());
                samplePet2.setTechnicalId(UUID.randomUUID());
                samplePet2.setName("Buddy");
                samplePet2.setType("dog");
                samplePet2.setBreed("Beagle");
                samplePet2.setAge(5);
                samplePet2.setAvailabilityStatus("AVAILABLE");
                samplePet2.setStatus("NEW");

                fetchedPets.add(samplePet1);
                fetchedPets.add(samplePet2);

                for (Pet pet : fetchedPets) {
                    if (pet.isValid()) {
                        petCache.put(pet.getId(), pet);
                        processPet(pet);
                    } else {
                        log.error("Invalid pet data during fetch: {}", pet);
                    }
                }
            } else if (action.equals("UPDATE_PET_STATUS")) {
                // For prototype, no specific update logic implemented
                // Would update pet availabilityStatus by creating new versions
                log.info("Update pet status action requested, no implementation in prototype");
            }

            job.setStatus("COMPLETED");
            log.info("Job {} completed successfully", job.getId());
        } catch (Exception e) {
            log.error("Error processing job {}", job.getId(), e);
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate fields again as business logic
        if (!pet.isValid()) {
            log.error("Validation failed for Pet ID: {}", pet.getId());
            return;
        }

        // Simulate enrichment or downstream processing
        if (pet.getStatus().equalsIgnoreCase("NEW")) {
            pet.setStatus("ACTIVE");
            log.info("Pet ID {} status updated to ACTIVE", pet.getId());
        }

        // Further processing could involve notifications, indexing, etc.
    }
}