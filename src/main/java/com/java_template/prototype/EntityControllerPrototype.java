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
import com.java_template.application.entity.CatFact;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, CatFact> catFactCache = new ConcurrentHashMap<>();
    private final AtomicLong catFactIdCounter = new AtomicLong(1);

    // POST /prototype/petjobs
    @PostMapping("/petjobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        try {
            if (petJob == null) {
                log.error("Received null PetJob");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetJob cannot be null");
            }

            // Assign IDs
            String newId = "petJob-" + petJobIdCounter.getAndIncrement();
            petJob.setId(newId);
            petJob.setTechnicalId(UUID.randomUUID());
            if (petJob.getCreatedAt() == null) {
                petJob.setCreatedAt(java.time.LocalDateTime.now());
            }
            if (petJob.getStatus() == null || petJob.getStatus().isBlank()) {
                petJob.setStatus("PENDING");
            }

            // Validate required fields
            if (!petJob.isValid()) {
                log.error("PetJob validation failed for id {}", newId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data");
            }

            petJobCache.put(newId, petJob);

            processPetJob(petJob);

            return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
        } catch (Exception e) {
            log.error("Error creating PetJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/petjobs/{id}
    @GetMapping("/petjobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // POST /prototype/pets
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            if (pet == null) {
                log.error("Received null Pet");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet cannot be null");
            }

            String newId = "pet-" + petIdCounter.getAndIncrement();
            pet.setId(newId);
            pet.setTechnicalId(UUID.randomUUID());
            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                pet.setStatus("ACTIVE");
            }

            if (!pet.isValid()) {
                log.error("Pet validation failed for id {}", newId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
            }

            petCache.put(newId, pet);

            processPet(pet);

            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/pets/{id}
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/catfacts
    @PostMapping("/catfacts")
    public ResponseEntity<?> createCatFact(@RequestBody CatFact catFact) {
        try {
            if (catFact == null) {
                log.error("Received null CatFact");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("CatFact cannot be null");
            }

            String newId = "catFact-" + catFactIdCounter.getAndIncrement();
            catFact.setId(newId);
            catFact.setTechnicalId(UUID.randomUUID());
            if (catFact.getStatus() == null || catFact.getStatus().isBlank()) {
                catFact.setStatus("PUBLISHED");
            }

            if (!catFact.isValid()) {
                log.error("CatFact validation failed for id {}", newId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid CatFact data");
            }

            catFactCache.put(newId, catFact);

            processCatFact(catFact);

            return ResponseEntity.status(HttpStatus.CREATED).body(catFact);
        } catch (Exception e) {
            log.error("Error creating CatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/catfacts/{id}
    @GetMapping("/catfacts/{id}")
    public ResponseEntity<?> getCatFact(@PathVariable String id) {
        CatFact catFact = catFactCache.get(id);
        if (catFact == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CatFact not found");
        }
        return ResponseEntity.ok(catFact);
    }

    // Business logic for PetJob
    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        // Validation of jobType and petId
        if (petJob.getJobType().equalsIgnoreCase("AddPet")) {
            // Trigger pet creation workflow - in prototype just log
            log.info("PetJob is AddPet job, triggering pet creation workflow for petId: {}", petJob.getPetId());
        } else if (petJob.getJobType().equalsIgnoreCase("UpdateStatus")) {
            // Handle status update - in prototype just log
            log.info("PetJob is UpdateStatus job for petId: {}", petJob.getPetId());
        } else {
            log.warn("PetJob has unknown jobType: {}", petJob.getJobType());
        }

        // Simulate completion
        petJob.setStatus("COMPLETED");
        petJobCache.put(petJob.getId(), petJob);

        log.info("PetJob ID: {} processing completed", petJob.getId());
    }

    // Business logic for Pet
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate pet type is supported
        List<String> supportedTypes = Arrays.asList("Cat", "Dog");
        if (!supportedTypes.contains(pet.getType())) {
            log.error("Unsupported pet type: {}", pet.getType());
            pet.setStatus("INACTIVE");
        } else {
            // Enrich pet data or trigger related jobs if needed
            log.info("Pet type {} is supported", pet.getType());
        }

        petCache.put(pet.getId(), pet);
        log.info("Pet ID: {} processing completed", pet.getId());
    }

    // Business logic for CatFact
    private void processCatFact(CatFact catFact) {
        log.info("Processing CatFact with ID: {}", catFact.getId());

        // Validate fact content length
        if (catFact.getFact().length() < 10) {
            log.error("CatFact fact is too short");
            catFact.setStatus("ARCHIVED");
        } else {
            log.info("CatFact fact is valid");
        }

        catFactCache.put(catFact.getId(), catFact);
        log.info("CatFact ID: {} processing completed", catFact.getId());
    }

}