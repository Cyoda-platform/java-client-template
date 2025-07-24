package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetIngestionJob> petIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petIngestionJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/petIngestionJob - Create PetIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) {
        if (job == null || job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing sourceUrl");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
        }

        // Generate IDs and set timestamps
        String newId = String.valueOf(petIngestionJobIdCounter.getAndIncrement());
        job.setId(newId);
        job.setTechnicalId(UUID.randomUUID());
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus("PENDING");

        petIngestionJobCache.put(newId, job);

        try {
            processPetIngestionJob(job);
        } catch (Exception e) {
            log.error("Error processing PetIngestionJob ID {}: {}", newId, e.getMessage());
            job.setStatus("FAILED");
            petIngestionJobCache.put(newId, job);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process PetIngestionJob");
        }

        log.info("Created PetIngestionJob with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /prototype/petIngestionJob/{id} - Retrieve PetIngestionJob by ID
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        PetIngestionJob job = petIngestionJobCache.get(id);
        if (job == null) {
            log.error("PetIngestionJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet - Create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Pet creation request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: name is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet creation failed: category is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE"); // default status
        }

        // Generate IDs
        String newId = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(newId);
        pet.setTechnicalId(UUID.randomUUID());

        petCache.put(newId, pet);

        try {
            processPet(pet);
        } catch (Exception e) {
            log.error("Error processing Pet ID {}: {}", newId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process Pet");
        }

        log.info("Created Pet with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - Retrieve Pet by ID
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/pet/{id}/update - Create new version of Pet (event)
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet updatedPet) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            log.error("Cannot update Pet - not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        if (updatedPet == null) {
            log.error("Update request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Updated Pet data is required");
        }
        if (updatedPet.getName() == null || updatedPet.getName().isBlank()) {
            log.error("Updated Pet name is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (updatedPet.getCategory() == null || updatedPet.getCategory().isBlank()) {
            log.error("Updated Pet category is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (updatedPet.getStatus() == null || updatedPet.getStatus().isBlank()) {
            updatedPet.setStatus(existingPet.getStatus());
        }

        // Create new version with new ID and technicalId
        String newId = String.valueOf(petIdCounter.getAndIncrement());
        updatedPet.setId(newId);
        updatedPet.setTechnicalId(UUID.randomUUID());

        petCache.put(newId, updatedPet);

        try {
            processPet(updatedPet);
        } catch (Exception e) {
            log.error("Error processing updated Pet ID {}: {}", newId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process updated Pet");
        }

        log.info("Created updated Pet version with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(updatedPet);
    }

    // POST /prototype/pet/{id}/deactivate - Create Pet deactivation event
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            log.error("Cannot deactivate Pet - not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        // Create new Pet entity representing deactivation
        Pet deactivatedPet = new Pet();
        deactivatedPet.setId(String.valueOf(petIdCounter.getAndIncrement()));
        deactivatedPet.setTechnicalId(UUID.randomUUID());
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setCategory(existingPet.getCategory());
        deactivatedPet.setPhotoUrls(existingPet.getPhotoUrls());
        deactivatedPet.setTags(existingPet.getTags());
        deactivatedPet.setStatus("DEACTIVATED");

        petCache.put(deactivatedPet.getId(), deactivatedPet);

        log.info("Deactivated Pet with new version ID: {}", deactivatedPet.getId());
        return ResponseEntity.ok("Pet deactivated with new version ID: " + deactivatedPet.getId());
    }

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with ID: {}", job.getId());

        // Validate sourceUrl
        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            job.setStatus("FAILED");
            petIngestionJobCache.put(job.getId(), job);
            log.error("PetIngestionJob failed validation: sourceUrl is blank");
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        job.setStatus("PROCESSING");
        petIngestionJobCache.put(job.getId(), job);

        // Simulate fetching data from Petstore API (simplified for prototype)
        // In real implementation, fetch HTTP data and parse JSON
        // Here we simulate creation of one pet entity for demonstration

        Pet newPet = new Pet();
        newPet.setId(String.valueOf(petIdCounter.getAndIncrement()));
        newPet.setTechnicalId(UUID.randomUUID());
        newPet.setName("SamplePetFromIngestion");
        newPet.setCategory("cat");
        newPet.setStatus("AVAILABLE");
        petCache.put(newPet.getId(), newPet);

        // Process new pet entity
        processPet(newPet);

        job.setStatus("COMPLETED");
        petIngestionJobCache.put(job.getId(), job);

        log.info("Completed processing PetIngestionJob with ID: {}", job.getId());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        if (!pet.isValid()) {
            log.error("Invalid Pet entity with ID: {}", pet.getId());
            throw new IllegalArgumentException("Pet entity validation failed");
        }

        // Business rules example:
        // If category is "cat", tag as "feline"
        if ("cat".equalsIgnoreCase(pet.getCategory())) {
            if (!pet.getTags().contains("feline")) {
                pet.getTags().add("feline");
            }
        }

        // Additional business logic like external API calls, notifications, etc. can be added here

        log.info("Pet processing complete for ID: {}", pet.getId());
    }
}