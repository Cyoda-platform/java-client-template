package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetUpdateEvent;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, PetUpdateEvent> petUpdateEventCache = new ConcurrentHashMap<>();
    private final AtomicLong petUpdateEventIdCounter = new AtomicLong(1);

    // ------------------- PetJob Endpoints -------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getJobId() == null || petJob.getJobId().isBlank()) {
            log.error("PetJob creation failed: Missing or blank jobId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("jobId is required");
        }
        String id = "job-" + petJobIdCounter.getAndIncrement();
        petJob.setId(id);
        petJob.setStatus("PENDING");
        petJob.setSubmittedAt(java.time.LocalDateTime.now());
        petJobCache.put(id, petJob);
        processPetJob(petJob);
        log.info("PetJob created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // ------------------- Pet Endpoints -------------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Pet creation failed: Request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: Missing or blank name");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
            log.error("Pet creation failed: Missing or blank species");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet species is required");
        }
        String id = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(id);
        pet.setStatus("ACTIVE");
        petCache.put(id, pet);
        processPet(pet);
        log.info("Pet created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // ------------------- PetUpdateEvent Endpoints -------------------

    @PostMapping("/pets/update")
    public ResponseEntity<?> createPetUpdateEvent(@RequestBody PetUpdateEvent petUpdateEvent) {
        if (petUpdateEvent == null) {
            log.error("PetUpdateEvent creation failed: Request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetUpdateEvent data is required");
        }
        if (petUpdateEvent.getPetId() == null || petUpdateEvent.getPetId().isBlank()) {
            log.error("PetUpdateEvent creation failed: Missing or blank petId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required");
        }
        if (petUpdateEvent.getUpdatedFields() == null || petUpdateEvent.getUpdatedFields().isEmpty()) {
            log.error("PetUpdateEvent creation failed: updatedFields is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("updatedFields are required");
        }
        String id = "event-" + petUpdateEventIdCounter.getAndIncrement();
        petUpdateEvent.setId(id);
        petUpdateEvent.setStatus("PENDING");
        petUpdateEventCache.put(id, petUpdateEvent);
        processPetUpdateEvent(petUpdateEvent);
        log.info("PetUpdateEvent created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(petUpdateEvent);
    }

    @GetMapping("/pets/update/{id}")
    public ResponseEntity<?> getPetUpdateEvent(@PathVariable String id) {
        PetUpdateEvent petUpdateEvent = petUpdateEventCache.get(id);
        if (petUpdateEvent == null) {
            log.error("PetUpdateEvent not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateEvent not found");
        }
        return ResponseEntity.ok(petUpdateEvent);
    }

    // ------------------- Process Methods -------------------

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        // Validate job parameters
        if (petJob.getJobId() == null || petJob.getJobId().isBlank()) {
            petJob.setStatus("FAILED");
            log.error("PetJob validation failed: jobId is blank");
            return;
        }

        // Simulate processing: create dummy pet entities (for example purposes)
        try {
            // Here you would fetch and process pet data, creating Pet entities
            // For prototype, just log and set status
            log.info("PetJob {} processing started", petJob.getJobId());
            petJob.setStatus("COMPLETED");
            log.info("PetJob {} processing completed successfully", petJob.getJobId());
        } catch (Exception e) {
            petJob.setStatus("FAILED");
            log.error("PetJob processing failed: {}", e.getMessage());
        }

        // Log or notify job result (omitted notification for prototype)
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate mandatory fields
        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getSpecies() == null || pet.getSpecies().isBlank()) {
            log.error("Pet validation failed: name or species is blank");
            return;
        }

        // Persist pet data immutably (already stored in cache)
        log.info("Pet {} persisted with status: {}", pet.getId(), pet.getStatus());
    }

    private void processPetUpdateEvent(PetUpdateEvent petUpdateEvent) {
        log.info("Processing PetUpdateEvent with ID: {}", petUpdateEvent.getId());

        // Validate petId and updatedFields
        if (petUpdateEvent.getPetId() == null || petUpdateEvent.getPetId().isBlank()) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent validation failed: petId is blank");
            return;
        }
        if (petUpdateEvent.getUpdatedFields() == null || petUpdateEvent.getUpdatedFields().isEmpty()) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent validation failed: updatedFields empty");
            return;
        }

        Pet existingPet = petCache.get(petUpdateEvent.getPetId());
        if (existingPet == null) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent processing failed: referenced Pet not found");
            return;
        }

        // Create new immutable Pet version with updated fields
        Pet updatedPet = new Pet();
        updatedPet.setId("pet-" + petIdCounter.getAndIncrement());
        updatedPet.setName(existingPet.getName());
        updatedPet.setSpecies(existingPet.getSpecies());
        updatedPet.setBreed(existingPet.getBreed());
        updatedPet.setAge(existingPet.getAge());
        updatedPet.setStatus(existingPet.getStatus());

        // Apply updates from updatedFields map
        Map<String, Object> updates = petUpdateEvent.getUpdatedFields();
        if (updates.containsKey("name")) {
            Object nameVal = updates.get("name");
            if (nameVal instanceof String && !((String) nameVal).isBlank()) {
                updatedPet.setName((String) nameVal);
            }
        }
        if (updates.containsKey("species")) {
            Object speciesVal = updates.get("species");
            if (speciesVal instanceof String && !((String) speciesVal).isBlank()) {
                updatedPet.setSpecies((String) speciesVal);
            }
        }
        if (updates.containsKey("breed")) {
            Object breedVal = updates.get("breed");
            if (breedVal instanceof String) {
                updatedPet.setBreed((String) breedVal);
            }
        }
        if (updates.containsKey("age")) {
            Object ageVal = updates.get("age");
            if (ageVal instanceof Number) {
                updatedPet.setAge(((Number) ageVal).intValue());
            }
        }
        if (updates.containsKey("status")) {
            Object statusVal = updates.get("status");
            if (statusVal instanceof String && !((String) statusVal).isBlank()) {
                updatedPet.setStatus((String) statusVal);
            }
        }

        // Save updated Pet version immutably
        petCache.put(updatedPet.getId(), updatedPet);
        petUpdateEvent.setStatus("PROCESSED");
        log.info("PetUpdateEvent {} processed successfully, created Pet version {}", petUpdateEvent.getId(), updatedPet.getId());
    }
}