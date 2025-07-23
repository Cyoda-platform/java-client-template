package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.PurrfectPetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PurrfectPetJob> purrfectPetJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, PetEvent> petEventCache = new ConcurrentHashMap<>();
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // --- PurrfectPetJob Endpoints ---

    @PostMapping("/purrfectPetJob")
    public ResponseEntity<?> createPurrfectPetJob(@RequestBody PurrfectPetJob job) {
        if (job == null) {
            log.error("Received null PurrfectPetJob");
            return ResponseEntity.badRequest().body("Job cannot be null");
        }

        job.setId("job-" + purrfectPetJobIdCounter.getAndIncrement());
        job.setTechnicalId(UUID.randomUUID());

        if (!job.isValid()) {
            log.error("Invalid PurrfectPetJob: {}", job);
            return ResponseEntity.badRequest().body("Invalid job data");
        }

        job.setStatus("PENDING");
        purrfectPetJobCache.put(job.getId(), job);

        log.info("Created PurrfectPetJob with ID: {}", job.getId());

        processPurrfectPetJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/purrfectPetJob/{id}")
    public ResponseEntity<?> getPurrfectPetJob(@PathVariable String id) {
        PurrfectPetJob job = purrfectPetJobCache.get(id);
        if (job == null) {
            log.error("PurrfectPetJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Received null Pet");
            return ResponseEntity.badRequest().body("Pet cannot be null");
        }

        pet.setId("pet-" + petIdCounter.getAndIncrement());
        pet.setTechnicalId(UUID.randomUUID());

        if (!pet.isValid()) {
            log.error("Invalid Pet data: {}", pet);
            return ResponseEntity.badRequest().body("Invalid pet data");
        }

        pet.setStatus("CREATED");
        petCache.put(pet.getId(), pet);

        log.info("Created Pet with ID: {}", pet.getId());

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // --- PetEvent Endpoints ---

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null) {
            log.error("Received null PetEvent");
            return ResponseEntity.badRequest().body("PetEvent cannot be null");
        }

        petEvent.setId("event-" + petEventIdCounter.getAndIncrement());
        petEvent.setTechnicalId(UUID.randomUUID());

        if (!petEvent.isValid()) {
            log.error("Invalid PetEvent data: {}", petEvent);
            return ResponseEntity.badRequest().body("Invalid pet event data");
        }

        petEventCache.put(petEvent.getId(), petEvent);

        log.info("Created PetEvent with ID: {}", petEvent.getId());

        processPetEvent(petEvent);

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

    // --- Process Methods ---

    private void processPurrfectPetJob(PurrfectPetJob job) {
        log.info("Processing PurrfectPetJob with ID: {}", job.getId());

        // Validate job parameters
        if (job.getPetType() == null || job.getPetType().isBlank() ||
            job.getAction() == null || job.getAction().isBlank()) {
            log.error("Invalid job parameters for job ID: {}", job.getId());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("PROCESSING");

        // Dispatch based on action
        try {
            if ("ADD".equalsIgnoreCase(job.getAction())) {
                // Parse payload JSON into Pet fields (simplified, assume payload is JSON string with name and age)
                // For prototype, we simulate parsing
                // In real case, use JSON parser to convert payload to Pet object
                Pet newPet = new Pet();
                newPet.setId("pet-" + petIdCounter.getAndIncrement());
                newPet.setTechnicalId(UUID.randomUUID());
                newPet.setType(job.getPetType());
                newPet.setStatus("CREATED");
                // Simulate minimal payload parsing for name and age
                String payload = job.getPayload();
                if (payload != null && !payload.isBlank()) {
                    // very simple parsing (not robust, for prototype only)
                    if (payload.contains("\"name\"")) {
                        int start = payload.indexOf("\"name\"") + 7;
                        int end = payload.indexOf("\"", start);
                        newPet.setName(payload.substring(start, end));
                    }
                    if (payload.contains("\"age\"")) {
                        int start = payload.indexOf("\"age\"") + 6;
                        int end = payload.indexOf("}", start);
                        try {
                            newPet.setAge(Integer.parseInt(payload.substring(start, end).trim()));
                        } catch (NumberFormatException e) {
                            newPet.setAge(0);
                        }
                    }
                }
                newPet.setAdoptionStatus("AVAILABLE");
                petCache.put(newPet.getId(), newPet);
                processPet(newPet);

                // Create PetEvent for creation
                PetEvent event = new PetEvent();
                event.setId("event-" + petEventIdCounter.getAndIncrement());
                event.setTechnicalId(UUID.randomUUID());
                event.setPetId(newPet.getId());
                event.setEventType("CREATED");
                event.setTimestamp(java.time.LocalDateTime.now());
                event.setStatus("RECORDED");
                petEventCache.put(event.getId(), event);
                processPetEvent(event);

            } else if ("SEARCH".equalsIgnoreCase(job.getAction())) {
                // For prototype, no actual search implementation, just log
                log.info("Search action received for petType: {}", job.getPetType());
            } else {
                log.warn("Unknown action: {} for job ID: {}", job.getAction(), job.getId());
                job.setStatus("FAILED");
                return;
            }

            job.setStatus("COMPLETED");
            log.info("Completed processing PurrfectPetJob with ID: {}", job.getId());

        } catch (Exception e) {
            log.error("Exception processing PurrfectPetJob with ID: {}", job.getId(), e);
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate required fields
        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getType() == null || pet.getType().isBlank()) {
            log.error("Invalid pet data for pet ID: {}", pet.getId());
            pet.setStatus("ARCHIVED"); // mark as invalid in prototype
            return;
        }

        // Enrichment example: if age is null, set default 0
        if (pet.getAge() == null) {
            pet.setAge(0);
        }

        pet.setStatus("ACTIVE");

        // Generate PetEvent with eventType CREATED already handled in job processing or creation

        log.info("Pet processing completed for ID: {}", pet.getId());
    }

    private void processPetEvent(PetEvent petEvent) {
        log.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank() ||
            petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            log.error("Invalid PetEvent data for event ID: {}", petEvent.getId());
            petEvent.setStatus("PROCESSED"); // mark invalid but processed
            return;
        }

        // Example: update pet adoption status if event type is ADOPTED
        if ("ADOPTED".equalsIgnoreCase(petEvent.getEventType())) {
            Pet pet = petCache.get(petEvent.getPetId());
            if (pet != null) {
                pet.setAdoptionStatus("ADOPTED");
                petCache.put(pet.getId(), pet);
                log.info("Updated adoption status to ADOPTED for pet ID: {}", pet.getId());
            } else {
                log.warn("Pet not found for PetEvent ID: {}, petId: {}", petEvent.getId(), petEvent.getPetId());
            }
        }

        petEvent.setStatus("PROCESSED");

        log.info("PetEvent processing completed for ID: {}", petEvent.getId());
    }
}