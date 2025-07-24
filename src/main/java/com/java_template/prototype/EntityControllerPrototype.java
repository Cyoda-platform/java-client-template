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

    // Cache and ID counter for PetJob
    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    // Cache and ID counter for Pet
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and ID counter for PetEvent
    private final ConcurrentHashMap<String, PetEvent> petEventCache = new ConcurrentHashMap<>();
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // POST /prototype/petJob - Create PetJob
    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getJobId() == null || petJob.getJobId().isBlank()
            || petJob.getPetType() == null || petJob.getPetType().isBlank()
            || petJob.getStatus() == null) {
            log.error("Invalid PetJob creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data");
        }

        String id = String.valueOf(petJobIdCounter.getAndIncrement());
        petJob.setId(id);
        petJobCache.put(id, petJob);

        log.info("Created PetJob with ID: {}", id);

        processPetJob(petJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    // GET /prototype/petJob/{id} - Retrieve PetJob by id
    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // POST /prototype/pet - Create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getPetId() == null || pet.getPetId().isBlank()
            || pet.getName() == null || pet.getName().isBlank()
            || pet.getCategory() == null || pet.getCategory().isBlank()
            || pet.getStatus() == null) {
            log.error("Invalid Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }

        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        petCache.put(id, pet);

        log.info("Created Pet with ID: {}", id);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - Retrieve Pet by id
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/petEvent - Create PetEvent
    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null || petEvent.getEventId() == null || petEvent.getEventId().isBlank()
            || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
            || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
            || petEvent.getTimestamp() == null
            || petEvent.getStatus() == null) {
            log.error("Invalid PetEvent creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetEvent data");
        }

        String id = String.valueOf(petEventIdCounter.getAndIncrement());
        petEvent.setId(id);
        petEventCache.put(id, petEvent);

        log.info("Created PetEvent with ID: {}", id);

        processPetEvent(petEvent);

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /prototype/petEvent/{id} - Retrieve PetEvent by id
    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

    // Business logic processing methods

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        // Validation
        if (petJob.getPetType().isBlank()) {
            log.error("PetJob petType is blank");
            petJob.setStatus(PetJob.StatusEnum.FAILED);
            return;
        }

        petJob.setStatus(PetJob.StatusEnum.PROCESSING);
        log.info("PetJob {} status updated to PROCESSING", petJob.getId());

        // Simulate fetching pets from external Petstore API for petType
        // For prototype, just create sample Pet entities
        for (int i = 1; i <= 3; i++) {
            Pet pet = new Pet();
            pet.setPetId(petJob.getPetType() + "-pet-" + i);
            pet.setName(petJob.getPetType().substring(0,1).toUpperCase() + "Pet" + i);
            pet.setCategory(petJob.getPetType());
            pet.setStatus(Pet.StatusEnum.AVAILABLE);

            // Assign business and technical IDs
            String petId = String.valueOf(petIdCounter.getAndIncrement());
            pet.setId(petId);

            petCache.put(petId, pet);

            log.info("Created Pet {} for PetJob {}", pet.getPetId(), petJob.getId());

            processPet(pet);

            // Create PetEvent for each Pet created
            PetEvent petEvent = new PetEvent();
            petEvent.setEventId("event-" + pet.getPetId());
            petEvent.setPetId(pet.getPetId());
            petEvent.setEventType("CREATED");
            petEvent.setTimestamp(java.time.LocalDateTime.now());
            petEvent.setStatus(PetEvent.StatusEnum.RECORDED);

            String petEventId = String.valueOf(petEventIdCounter.getAndIncrement());
            petEvent.setId(petEventId);

            petEventCache.put(petEventId, petEvent);

            log.info("Created PetEvent {} for Pet {}", petEvent.getEventId(), pet.getPetId());

            processPetEvent(petEvent);
        }

        petJob.setStatus(PetJob.StatusEnum.COMPLETED);
        log.info("PetJob {} processing COMPLETED", petJob.getId());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate pet data integrity
        if (pet.getPetId().isBlank() || pet.getName().isBlank() || pet.getCategory().isBlank()) {
            log.error("Pet data validation failed for ID: {}", pet.getId());
            return;
        }

        // Optional enrichment/indexing can be done here
        log.info("Pet {} data validated and ready for retrieval", pet.getPetId());
    }

    private void processPetEvent(PetEvent petEvent) {
        log.info("Processing PetEvent with ID: {}", petEvent.getId());

        // Analyze event - e.g., update stats or trigger further workflows
        log.info("PetEvent {} of type {} processed at {}", petEvent.getEventId(), petEvent.getEventType(), petEvent.getTimestamp());

        // Mark event as PROCESSED
        petEvent.setStatus(PetEvent.StatusEnum.PROCESSED);
    }
}