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

    // POST /prototype/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null) {
            log.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }
        String id = "job-" + petJobIdCounter.getAndIncrement();
        petJob.setId(id);
        petJob.setTechnicalId(UUID.randomUUID());
        petJob.setStatus("PENDING");
        petJobCache.put(id, petJob);
        log.info("Created PetJob with ID: {}", id);

        try {
            processPetJob(petJob);
        } catch (Exception e) {
            log.error("Error processing PetJob with ID: {}", id, e);
            petJob.setStatus("FAILED");
            petJobCache.put(id, petJob);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", id, "status", petJob.getStatus()));
    }

    // GET /prototype/petjob/{id} - get PetJob by ID
    @GetMapping("/petjob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // POST /prototype/pet/{id} - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getAge() == null || pet.getAge() < 0) {
            log.error("Invalid Pet creation request: missing or invalid fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid fields: name, species, age");
        }
        String id = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());
        pet.setStatus("ACTIVE");
        petCache.put(id, pet);
        log.info("Created Pet with ID: {}", id);

        try {
            processPet(pet);
        } catch (Exception e) {
            log.error("Error processing Pet with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - get Pet by ID
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/petevent - create PetEvent entity
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
                || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
                || petEvent.getEventTimestamp() == null) {
            log.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: petId, eventType, eventTimestamp");
        }
        String id = "event-" + petEventIdCounter.getAndIncrement();
        petEvent.setId(id);
        petEvent.setTechnicalId(UUID.randomUUID());
        petEvent.setStatus("RECORDED");
        petEventCache.put(id, petEvent);
        log.info("Created PetEvent with ID: {}", id);

        try {
            processPetEvent(petEvent);
        } catch (Exception e) {
            log.error("Error processing PetEvent with ID: {}", id, e);
            petEvent.setStatus("FAILED");
            petEventCache.put(id, petEvent);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetEvent");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /prototype/petevent/{id} - get PetEvent by ID
    @GetMapping("/petevent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        // Validate jobType and payload
        String jobType = petJob.getJobType();
        if (jobType == null || jobType.isBlank()) {
            log.error("PetJob jobType is missing or blank");
            petJob.setStatus("FAILED");
            petJobCache.put(petJob.getId(), petJob);
            throw new IllegalArgumentException("jobType is required");
        }

        Map<String, Object> payload = petJob.getPayload();
        if (payload == null || payload.isEmpty()) {
            log.error("PetJob payload is missing or empty");
            petJob.setStatus("FAILED");
            petJobCache.put(petJob.getId(), petJob);
            throw new IllegalArgumentException("payload is required");
        }

        petJob.setStatus("PROCESSING");
        petJobCache.put(petJob.getId(), petJob);

        try {
            if ("AddPet".equalsIgnoreCase(jobType)) {
                // Extract pet info from payload
                String name = (String) payload.get("name");
                String species = (String) payload.get("species");
                Integer age = null;
                Object ageObj = payload.get("age");
                if (ageObj instanceof Integer) {
                    age = (Integer) ageObj;
                } else if (ageObj instanceof Number) {
                    age = ((Number) ageObj).intValue();
                }

                if (name == null || name.isBlank() || species == null || species.isBlank() || age == null || age < 0) {
                    log.error("Invalid pet data in PetJob payload");
                    petJob.setStatus("FAILED");
                    petJobCache.put(petJob.getId(), petJob);
                    throw new IllegalArgumentException("Invalid pet data in payload");
                }

                // Create new Pet entity
                Pet pet = new Pet();
                String petId = "pet-" + petIdCounter.getAndIncrement();
                pet.setId(petId);
                pet.setTechnicalId(UUID.randomUUID());
                pet.setName(name);
                pet.setSpecies(species);
                pet.setAge(age);
                pet.setStatus("ACTIVE");
                petCache.put(petId, pet);
                log.info("Created Pet with ID: {} via PetJob", petId);

                // Create PetEvent for created pet
                PetEvent petEvent = new PetEvent();
                String eventId = "event-" + petEventIdCounter.getAndIncrement();
                petEvent.setId(eventId);
                petEvent.setTechnicalId(UUID.randomUUID());
                petEvent.setPetId(petId);
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(new Date());
                petEvent.setStatus("RECORDED");
                petEventCache.put(eventId, petEvent);
                log.info("Created PetEvent with ID: {} for Pet ID: {}", eventId, petId);

                processPetEvent(petEvent);

            } else if ("UpdatePetInfo".equalsIgnoreCase(jobType)) {
                // For this prototype, we avoid update; real logic would create new Pet version
                log.info("UpdatePetInfo jobType not implemented as this is prototype");
            } else {
                log.error("Unsupported jobType in PetJob: {}", jobType);
                petJob.setStatus("FAILED");
                petJobCache.put(petJob.getId(), petJob);
                throw new IllegalArgumentException("Unsupported jobType: " + jobType);
            }

            petJob.setStatus("COMPLETED");
            petJobCache.put(petJob.getId(), petJob);
            log.info("PetJob with ID: {} completed successfully", petJob.getId());

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            petJobCache.put(petJob.getId(), petJob);
            log.error("Exception during processing PetJob with ID: {}", petJob.getId(), e);
            throw e;
        }
    }

    private void processPetEvent(PetEvent petEvent) {
        log.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            log.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            log.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            log.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        // Example business logic: here could be workflow triggers, notifications, etc.
        // For prototype, just mark as processed
        petEvent.setStatus("PROCESSED");
        petEventCache.put(petEvent.getId(), petEvent);
        log.info("PetEvent with ID: {} processed successfully", petEvent.getId());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Example business logic: validate pet data, enrich, external calls, etc.
        // For prototype, assume pet is valid and active
        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getSpecies() == null || pet.getSpecies().isBlank() ||
            pet.getAge() == null || pet.getAge() < 0) {
            log.error("Invalid Pet data");
            throw new IllegalArgumentException("Invalid Pet data");
        }
        log.info("Pet with ID: {} is valid and active", pet.getId());
    }
}