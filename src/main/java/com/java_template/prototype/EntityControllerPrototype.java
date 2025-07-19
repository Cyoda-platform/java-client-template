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
import com.java_template.application.entity.PetEvent;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for entities
    private final ConcurrentHashMap<String, PetUpdateJob> petUpdateJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petUpdateJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, PetEvent> petEventCache = new ConcurrentHashMap<>();
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // POST /prototype/petUpdateJob - create new PetUpdateJob
    @PostMapping("/petUpdateJob")
    public ResponseEntity<?> createPetUpdateJob(@RequestBody PetUpdateJob job) {
        if (job == null) {
            log.error("PetUpdateJob creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body required");
        }
        // Generate business ID if missing
        if (job.getJobId() == null || job.getJobId().isBlank()) {
            job.setJobId("job-" + petUpdateJobIdCounter.getAndIncrement());
        }
        // Set requestedAt if missing
        if (job.getRequestedAt() == null) {
            job.setRequestedAt(java.time.LocalDateTime.now());
        }
        // Default status to PENDING if missing or blank
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (!job.isValid()) {
            log.error("PetUpdateJob creation failed: validation failed for jobId {}", job.getJobId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetUpdateJob data");
        }
        petUpdateJobCache.put(job.getJobId(), job);
        log.info("Created PetUpdateJob with jobId {}", job.getJobId());

        processPetUpdateJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /prototype/petUpdateJob/{jobId} - get PetUpdateJob by id
    @GetMapping("/petUpdateJob/{jobId}")
    public ResponseEntity<?> getPetUpdateJob(@PathVariable String jobId) {
        PetUpdateJob job = petUpdateJobCache.get(jobId);
        if (job == null) {
            log.error("PetUpdateJob not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet - create new Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Pet creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body required");
        }
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            pet.setPetId("pet-" + petIdCounter.getAndIncrement());
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }
        if (!pet.isValid()) {
            log.error("Pet creation failed: validation failed for petId {}", pet.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }
        petCache.put(pet.getPetId(), pet);
        log.info("Created Pet with petId {}", pet.getPetId());

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{petId} - get Pet by id
    @GetMapping("/pet/{petId}")
    public ResponseEntity<?> getPet(@PathVariable String petId) {
        Pet pet = petCache.get(petId);
        if (pet == null) {
            log.error("Pet not found: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/petEvent - create new PetEvent
    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent event) {
        if (event == null) {
            log.error("PetEvent creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body required");
        }
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId("event-" + petEventIdCounter.getAndIncrement());
        }
        if (event.getStatus() == null || event.getStatus().isBlank()) {
            event.setStatus("RECEIVED");
        }
        if (event.getEventTimestamp() == null) {
            event.setEventTimestamp(java.time.LocalDateTime.now());
        }
        if (!event.isValid()) {
            log.error("PetEvent creation failed: validation failed for eventId {}", event.getEventId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetEvent data");
        }
        petEventCache.put(event.getEventId(), event);
        log.info("Created PetEvent with eventId {}", event.getEventId());

        processPetEvent(event);

        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    // GET /prototype/petEvent/{eventId} - get PetEvent by id
    @GetMapping("/petEvent/{eventId}")
    public ResponseEntity<?> getPetEvent(@PathVariable String eventId) {
        PetEvent event = petEventCache.get(eventId);
        if (event == null) {
            log.error("PetEvent not found: {}", eventId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(event);
    }

    // Business logic implementations for event-driven processing

    private void processPetUpdateJob(PetUpdateJob job) {
        log.info("Processing PetUpdateJob with ID: {}", job.getJobId());
        // Validate job parameters
        if (job.getSource() == null || job.getSource().isBlank()) {
            log.error("PetUpdateJob processing failed: source is blank");
            job.setStatus("FAILED");
            return;
        }
        job.setStatus("PROCESSING");

        // Simulate fetching pet data from external Petstore API
        // For prototype, we simulate some pets created
        try {
            for (int i = 0; i < 3; i++) {
                Pet newPet = new Pet();
                newPet.setPetId("pet-auto-" + UUID.randomUUID());
                newPet.setName("AutoPet" + i);
                newPet.setCategory(i % 2 == 0 ? "cat" : "dog");
                newPet.setStatus("AVAILABLE");
                newPet.setId(UUID.randomUUID().toString());
                newPet.setTechnicalId(UUID.randomUUID());
                petCache.put(newPet.getPetId(), newPet);
                processPet(newPet);

                PetEvent petEvent = new PetEvent();
                petEvent.setEventId("event-auto-" + UUID.randomUUID());
                petEvent.setPetId(newPet.getPetId());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(java.time.LocalDateTime.now());
                petEvent.setPayload("{\"name\":\"" + newPet.getName() + "\"}");
                petEvent.setStatus("RECEIVED");
                petEvent.setId(UUID.randomUUID().toString());
                petEvent.setTechnicalId(UUID.randomUUID());
                petEventCache.put(petEvent.getEventId(), petEvent);
                processPetEvent(petEvent);
            }
            job.setStatus("COMPLETED");
            log.info("PetUpdateJob {} completed successfully", job.getJobId());
        } catch (Exception e) {
            log.error("Error processing PetUpdateJob {}: {}", job.getJobId(), e.getMessage());
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getPetId());
        // Validate fields are present (already validated in isValid)
        // Enrich or trigger downstream workflows if needed
        // For prototype, just log success
        log.info("Pet {} is currently in status {}", pet.getPetId(), pet.getStatus());
    }

    private void processPetEvent(PetEvent event) {
        log.info("Processing PetEvent with ID: {}", event.getEventId());
        // Confirm associated Pet exists
        Pet relatedPet = petCache.get(event.getPetId());
        if (relatedPet == null) {
            log.error("PetEvent {} processing failed: related Pet {} not found", event.getEventId(), event.getPetId());
            event.setStatus("FAILED");
            return;
        }
        // Apply event payload to Pet state if needed (simulate)
        // For prototype, just mark event as processed
        event.setStatus("PROCESSED");
        log.info("PetEvent {} processed successfully for Pet {}", event.getEventId(), event.getPetId());
    }
}