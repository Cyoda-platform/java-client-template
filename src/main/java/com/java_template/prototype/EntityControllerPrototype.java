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

    // Cache and ID counters for PurrfectPetsJob
    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    // Cache and ID counters for Pet
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // -------- PurrfectPetsJob Endpoints --------

    @PostMapping("/jobs")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        if (job == null) {
            log.error("Received null job request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Job data must be provided");
        }
        // Generate business id
        String newId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setId(newId);
        job.setJobId(newId);
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (!job.isValid()) {
            log.error("Invalid job data for id {}", newId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid job data");
        }
        purrfectPetsJobCache.put(newId, job);
        log.info("Created PurrfectPetsJob with ID: {}", newId);

        processPurrfectPetsJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", newId, "status", job.getStatus()));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            log.error("PurrfectPetsJob not found for id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // -------- Pet Endpoints --------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Received null pet request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data must be provided");
        }
        // Generate business id
        String newId = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(newId);
        pet.setPetId(newId);
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("NEW");
        }
        if (!pet.isValid()) {
            log.error("Invalid pet data for id {}", newId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data");
        }
        petCache.put(newId, pet);
        log.info("Created Pet with ID: {}", newId);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("petId", newId, "status", pet.getStatus()));
    }

    @GetMapping("/pets")
    public ResponseEntity<?> getAllPets() {
        Collection<Pet> pets = petCache.values();
        return ResponseEntity.ok(pets);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPetById(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found for id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // -------- Process Methods --------

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());
        // Business logic:
        // 1. Validate action and payload
        if (job.getAction() == null || job.getAction().isBlank()) {
            log.error("Job action is missing");
            job.setStatus("FAILED");
            return;
        }
        if ("ingestPetData".equals(job.getAction())) {
            // Simulate ingestion by logging and possibly creating Pet entities
            log.info("Ingesting pet data from payload: {}", job.getPayload());
            // Normally, here would be API call to fetch pet data and create Pet entities
            // For prototype, simulate creating one Pet entity from the ingestion
            Pet newPet = new Pet();
            String newPetId = "pet-" + petIdCounter.getAndIncrement();
            newPet.setId(newPetId);
            newPet.setPetId(newPetId);
            newPet.setName("Sample Pet");
            newPet.setCategory("cat");
            newPet.setStatus("NEW");
            newPet.setPhotoUrls(new ArrayList<>());
            newPet.setTags(new ArrayList<>());
            petCache.put(newPetId, newPet);
            processPet(newPet);
            log.info("Created new pet '{}' from ingestion", newPet.getName());
            job.setStatus("COMPLETED");
        } else {
            log.error("Unknown job action: {}", job.getAction());
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Business logic:
        // 1. Validate mandatory fields (done in isValid)
        // 2. Change status from NEW to ACTIVE after validation
        if ("NEW".equalsIgnoreCase(pet.getStatus())) {
            pet.setStatus("ACTIVE");
            log.info("Pet {} status set to ACTIVE", pet.getId());
        }
        // 3. Prepare pet data for search/indexing (simulated)
        log.info("Pet {} is ready for retrieval", pet.getId());
    }
}