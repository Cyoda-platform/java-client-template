package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetIngestionJob> petIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petIngestionJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // POST /prototype/petIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob petIngestionJob) {
        if (petIngestionJob == null || petIngestionJob.getSource() == null || petIngestionJob.getSource().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing source");
            return ResponseEntity.badRequest().body("Missing required field: source");
        }
        String newId = "job-" + petIngestionJobIdCounter.getAndIncrement();
        petIngestionJob.setId(newId);
        petIngestionJob.setStatus("PENDING");
        petIngestionJob.setCreatedAt(java.time.LocalDateTime.now());
        petIngestionJobCache.put(newId, petIngestionJob);

        processPetIngestionJob(petIngestionJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(petIngestionJob);
    }

    // GET /prototype/petIngestionJob/{id}
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        PetIngestionJob job = petIngestionJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getType() == null || pet.getType().isBlank()
                || pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: name, type, status");
        }
        String newId = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(newId);
        pet.setCreatedAt(java.time.LocalDateTime.now());
        petCache.put(newId, pet);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id}
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/pet/{id}/update
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        if (petUpdate == null || petUpdate.getName() == null || petUpdate.getName().isBlank()
                || petUpdate.getType() == null || petUpdate.getType().isBlank()
                || petUpdate.getStatus() == null || petUpdate.getStatus().isBlank()) {
            log.error("Invalid Pet update request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: name, type, status");
        }
        String newId = "pet-" + petIdCounter.getAndIncrement();
        Pet newPetVersion = new Pet();
        newPetVersion.setId(newId);
        newPetVersion.setName(petUpdate.getName());
        newPetVersion.setType(petUpdate.getType());
        newPetVersion.setStatus(petUpdate.getStatus());
        newPetVersion.setCreatedAt(java.time.LocalDateTime.now());
        petCache.put(newId, newPetVersion);

        processPet(newPetVersion);

        return ResponseEntity.status(HttpStatus.CREATED).body(newPetVersion);
    }

    // POST /prototype/pet/{id}/deactivate
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        String newId = "pet-" + petIdCounter.getAndIncrement();
        Pet deactivatedPet = new Pet();
        deactivatedPet.setId(newId);
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setType(existingPet.getType());
        deactivatedPet.setStatus("DEACTIVATED");
        deactivatedPet.setCreatedAt(java.time.LocalDateTime.now());
        petCache.put(newId, deactivatedPet);

        log.info("Pet with original ID {} deactivated by creating new entity with ID {}", id, newId);

        return ResponseEntity.ok("Pet deactivated successfully");
    }

    // POST /prototype/adoptionRequest
    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) {
        if (adoptionRequest == null || adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isBlank()
                || adoptionRequest.getAdopterName() == null || adoptionRequest.getAdopterName().isBlank()
                || adoptionRequest.getStatus() == null || adoptionRequest.getStatus().isBlank()) {
            log.error("Invalid AdoptionRequest creation request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: petId, adopterName, status");
        }
        // Check pet availability
        Pet pet = petCache.get(adoptionRequest.getPetId());
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced pet does not exist");
        }
        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet is not available for adoption");
        }

        String newId = "req-" + adoptionRequestIdCounter.getAndIncrement();
        adoptionRequest.setId(newId);
        adoptionRequest.setStatus("SUBMITTED");
        adoptionRequest.setCreatedAt(java.time.LocalDateTime.now());
        adoptionRequestCache.put(newId, adoptionRequest);

        processAdoptionRequest(adoptionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(adoptionRequest);
    }

    // GET /prototype/adoptionRequest/{id}
    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        AdoptionRequest req = adoptionRequestCache.get(id);
        if (req == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(req);
    }

    // process methods with real business logic

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with ID: {}", job.getId());
        job.setStatus("PROCESSING");
        try {
            // Validate source URL format (simple check)
            if (!job.getSource().startsWith("http")) {
                throw new IllegalArgumentException("Invalid source URL");
            }
            // Simulate fetching pet data from Petstore API - here just create dummy pets for demo
            // In real implementation, call external API and parse response
            Pet pet1 = new Pet();
            pet1.setId("pet-" + petIdCounter.getAndIncrement());
            pet1.setName("Fluffy");
            pet1.setType("Cat");
            pet1.setStatus("AVAILABLE");
            pet1.setCreatedAt(java.time.LocalDateTime.now());
            petCache.put(pet1.getId(), pet1);
            processPet(pet1);

            Pet pet2 = new Pet();
            pet2.setId("pet-" + petIdCounter.getAndIncrement());
            pet2.setName("Buddy");
            pet2.setType("Dog");
            pet2.setStatus("AVAILABLE");
            pet2.setCreatedAt(java.time.LocalDateTime.now());
            petCache.put(pet2.getId(), pet2);
            processPet(pet2);

            job.setStatus("COMPLETED");
            log.info("PetIngestionJob {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("PetIngestionJob {} failed: {}", job.getId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate pet data completeness
        if (!pet.isValid()) {
            log.error("Pet {} is invalid", pet.getId());
            return;
        }
        // Optional enrichment or alert logic (e.g. notify if pet is rare breed)
        // For demo, simply log
        log.info("Pet {} is ready and available with status {}", pet.getId(), pet.getStatus());
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        log.info("Processing AdoptionRequest with ID: {}", request.getId());
        Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            log.error("Referenced pet {} does not exist for adoption request {}", request.getPetId(), request.getId());
            request.setStatus("REJECTED");
            return;
        }
        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            log.info("Pet {} is not available for adoption, rejecting request {}", pet.getId(), request.getId());
            request.setStatus("REJECTED");
            return;
        }
        // For demo, approve all requests where pet is available
        request.setStatus("APPROVED");
        log.info("AdoptionRequest {} approved for pet {}", request.getId(), pet.getId());

        // Create new Pet immutable state for ADOPTED
        Pet adoptedPet = new Pet();
        adoptedPet.setId("pet-" + petIdCounter.getAndIncrement());
        adoptedPet.setName(pet.getName());
        adoptedPet.setType(pet.getType());
        adoptedPet.setStatus("ADOPTED");
        adoptedPet.setCreatedAt(java.time.LocalDateTime.now());
        petCache.put(adoptedPet.getId(), adoptedPet);
        log.info("Pet {} status updated to ADOPTED by creating new entity {}", pet.getId(), adoptedPet.getId());
    }
}