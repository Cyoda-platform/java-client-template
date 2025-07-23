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

    private final ConcurrentHashMap<String, com.java_template.application.entity.PetRegistrationJob> petRegistrationJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petRegistrationJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.PetEvent> petEventCache = new ConcurrentHashMap<>();
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // POST /prototype/petRegistrationJob - Create PetRegistrationJob
    @PostMapping("/petRegistrationJob")
    public ResponseEntity<?> createPetRegistrationJob(@RequestBody com.java_template.application.entity.PetRegistrationJob job) {
        try {
            if (job == null) {
                log.error("Received null PetRegistrationJob");
                return ResponseEntity.badRequest().body("PetRegistrationJob cannot be null");
            }
            // Generate IDs
            String id = "job-" + petRegistrationJobIdCounter.getAndIncrement();
            job.setId(id);
            job.setJobId(id);
            job.setStatus("PENDING");
            // Simple validation
            if (!job.isValid()) {
                log.error("Invalid PetRegistrationJob: {}", job);
                return ResponseEntity.badRequest().body("Invalid PetRegistrationJob data");
            }
            petRegistrationJobCache.put(id, job);
            log.info("Created PetRegistrationJob with ID: {}", id);
            processPetRegistrationJob(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(job);
        } catch (Exception e) {
            log.error("Error creating PetRegistrationJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/petRegistrationJob/{id} - Retrieve PetRegistrationJob by id
    @GetMapping("/petRegistrationJob/{id}")
    public ResponseEntity<?> getPetRegistrationJob(@PathVariable String id) {
        com.java_template.application.entity.PetRegistrationJob job = petRegistrationJobCache.get(id);
        if (job == null) {
            log.error("PetRegistrationJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetRegistrationJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet - Create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody com.java_template.application.entity.Pet pet) {
        try {
            if (pet == null) {
                log.error("Received null Pet");
                return ResponseEntity.badRequest().body("Pet cannot be null");
            }
            String id = "pet-" + petIdCounter.getAndIncrement();
            pet.setId(id);
            pet.setPetId(id);
            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                pet.setStatus("ACTIVE");
            }
            if (pet.getRegisteredAt() == null || pet.getRegisteredAt().isBlank()) {
                pet.setRegisteredAt(new Date().toInstant().toString());
            }
            if (!pet.isValid()) {
                log.error("Invalid Pet data: {}", pet);
                return ResponseEntity.badRequest().body("Invalid Pet data");
            }
            petCache.put(id, pet);
            log.info("Created Pet with ID: {}", id);
            processPet(pet);
            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/pet/{id} - Retrieve Pet by id
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        com.java_template.application.entity.Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // GET /prototype/pet - List all Pets
    @GetMapping("/pet")
    public ResponseEntity<?> listPets() {
        Collection<com.java_template.application.entity.Pet> pets = petCache.values();
        return ResponseEntity.ok(pets);
    }

    // POST /prototype/petEvent - Create PetEvent
    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody com.java_template.application.entity.PetEvent event) {
        try {
            if (event == null) {
                log.error("Received null PetEvent");
                return ResponseEntity.badRequest().body("PetEvent cannot be null");
            }
            String id = "event-" + petEventIdCounter.getAndIncrement();
            event.setId(id);
            event.setEventId(id);
            if (event.getStatus() == null || event.getStatus().isBlank()) {
                event.setStatus("RECORDED");
            }
            if (event.getEventTimestamp() == null || event.getEventTimestamp().isBlank()) {
                event.setEventTimestamp(new Date().toInstant().toString());
            }
            if (!event.isValid()) {
                log.error("Invalid PetEvent data: {}", event);
                return ResponseEntity.badRequest().body("Invalid PetEvent data");
            }
            petEventCache.put(id, event);
            log.info("Created PetEvent with ID: {}", id);
            processPetEvent(event);
            return ResponseEntity.status(HttpStatus.CREATED).body(event);
        } catch (Exception e) {
            log.error("Error creating PetEvent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/petEvent/{id} - Retrieve PetEvent by id
    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        com.java_template.application.entity.PetEvent event = petEventCache.get(id);
        if (event == null) {
            log.error("PetEvent not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(event);
    }

    // Business logic for PetRegistrationJob
    private void processPetRegistrationJob(com.java_template.application.entity.PetRegistrationJob job) {
        log.info("Processing PetRegistrationJob with ID: {}", job.getId());
        // Validate job details
        if (!job.isValid()) {
            log.error("Invalid PetRegistrationJob during processing: {}", job);
            job.setStatus("FAILED");
            petRegistrationJobCache.put(job.getId(), job);
            return;
        }
        // Create Pet entity from job
        com.java_template.application.entity.Pet pet = new com.java_template.application.entity.Pet();
        pet.setPetId("pet-" + petIdCounter.getAndIncrement());
        pet.setId(pet.getPetId());
        pet.setName(job.getPetName());
        pet.setType(job.getPetType());
        pet.setOwner(job.getOwnerName());
        pet.setRegisteredAt(new Date().toInstant().toString());
        pet.setStatus("ACTIVE");
        petCache.put(pet.getId(), pet);
        log.info("Created Pet {} from Job {}", pet.getId(), job.getId());

        // Create PetEvent entity for creation event
        com.java_template.application.entity.PetEvent event = new com.java_template.application.entity.PetEvent();
        event.setEventId("event-" + petEventIdCounter.getAndIncrement());
        event.setId(event.getEventId());
        event.setPetId(pet.getId());
        event.setEventType("CREATED");
        event.setEventTimestamp(new Date().toInstant().toString());
        event.setStatus("RECORDED");
        petEventCache.put(event.getId(), event);
        log.info("Created PetEvent {} for Pet {}", event.getId(), pet.getId());

        // Update job status to COMPLETED
        job.setStatus("COMPLETED");
        petRegistrationJobCache.put(job.getId(), job);
        log.info("PetRegistrationJob {} completed", job.getId());
    }

    // Business logic for Pet entity
    private void processPet(com.java_template.application.entity.Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Here, you could enrich pet data, trigger notifications, etc.
        // For prototype, just log success
        log.info("Pet {} processed successfully", pet.getId());
    }

    // Business logic for PetEvent entity
    private void processPetEvent(com.java_template.application.entity.PetEvent event) {
        log.info("Processing PetEvent with ID: {}", event.getId());
        if (!event.isValid()) {
            log.error("Invalid PetEvent during processing: {}", event);
            event.setStatus("FAILED");
            petEventCache.put(event.getId(), event);
            return;
        }
        // Example: Notify owner or trigger workflows here
        event.setStatus("PROCESSED");
        petEventCache.put(event.getId(), event);
        log.info("PetEvent {} processed successfully", event.getId());
    }
}