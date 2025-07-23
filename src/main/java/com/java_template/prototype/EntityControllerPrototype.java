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
import com.java_template.application.entity.PetEvent;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, PetEvent> petEventCache = new ConcurrentHashMap<>();
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // --- PetJob Endpoints ---

    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
            log.error("Invalid PetJob creation request: sourceUrl missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
        }

        String generatedId = "PJ" + petJobIdCounter.getAndIncrement();
        petJob.setId(generatedId);
        petJob.setJobId(generatedId);
        petJob.setStatus("PENDING");
        petJobCache.put(generatedId, petJob);

        processPetJob(petJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() ||
            pet.getCategory() == null || pet.getCategory().isBlank() ||
            pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name, category, and status are required");
        }

        String generatedId = "P" + petIdCounter.getAndIncrement();
        pet.setId(generatedId);
        pet.setPetId(generatedId);
        petCache.put(generatedId, pet);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // --- PetEvent Endpoints ---

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null || petEvent.getEventType() == null || petEvent.getEventType().isBlank() ||
            petEvent.getPetId() == null || petEvent.getPetId().isBlank() ||
            petEvent.getStatus() == null || petEvent.getStatus().isBlank() ||
            petEvent.getTimestamp() == null) {
            log.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("eventType, petId, timestamp, and status are required");
        }

        String generatedId = "PE" + petEventIdCounter.getAndIncrement();
        petEvent.setId(generatedId);
        petEvent.setEventId(generatedId);
        petEventCache.put(generatedId, petEvent);

        processPetEvent(petEvent);

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

    // --- Process Methods ---

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        try {
            petJob.setStatus("PROCESSING");

            // Validate URL accessibility (basic check)
            if (petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("Source URL is blank");
            }

            // Simulate fetching pet data from Petstore API
            // For prototype, no real HTTP call is made

            // Simulate creating Pet entities from fetched data
            Pet samplePet = new Pet();
            samplePet.setId("P" + petIdCounter.getAndIncrement());
            samplePet.setPetId(samplePet.getId());
            samplePet.setName("SampleCat");
            samplePet.setCategory("cat");
            samplePet.setStatus("AVAILABLE");
            petCache.put(samplePet.getId(), samplePet);
            processPet(samplePet);

            petJob.setStatus("COMPLETED");
            log.info("PetJob {} completed successfully", petJob.getId());
        } catch (Exception e) {
            petJob.setStatus("FAILED");
            log.error("PetJob {} failed: {}", petJob.getId(), e.getMessage());
        }

        petJobCache.put(petJob.getId(), petJob);
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        if (!pet.isValid()) {
            log.error("Pet validation failed for ID: {}", pet.getId());
            return;
        }

        // Optionally add fun pet puns or enrichment (simulate)
        if ("cat".equalsIgnoreCase(pet.getCategory())) {
            pet.setName(pet.getName() + " the Purrfect");
        }

        // Example status update logic (immutable creation preferred, but for prototype we just log)
        log.info("Pet {} is currently {}", pet.getId(), pet.getStatus());

        // Create PetEvent for CREATED event
        PetEvent petEvent = new PetEvent();
        petEvent.setId("PE" + petEventIdCounter.getAndIncrement());
        petEvent.setEventId(petEvent.getId());
        petEvent.setPetId(pet.getId());
        petEvent.setEventType("CREATED");
        petEvent.setTimestamp(java.time.LocalDateTime.now());
        petEvent.setStatus("RECORDED");
        petEventCache.put(petEvent.getId(), petEvent);

        processPetEvent(petEvent);
    }

    private void processPetEvent(PetEvent petEvent) {
        log.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (!petEvent.isValid()) {
            log.error("PetEvent validation failed for ID: {}", petEvent.getId());
            return;
        }

        // Log event for audit/tracking
        log.info("Event {} for Pet {} of type {} recorded at {}", petEvent.getId(), petEvent.getPetId(), petEvent.getEventType(), petEvent.getTimestamp());
    }
}