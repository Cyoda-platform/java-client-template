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

    // -------- PetIngestionJob Endpoints --------

    @PostMapping("/jobs/pet-ingestion")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) {
        if (job == null || job.getSource() == null || job.getSource().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing source");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Source is required");
        }
        String newId = "job-" + petIngestionJobIdCounter.getAndIncrement();
        job.setId(newId);
        job.setJobId(newId);
        job.setCreatedAt(java.time.LocalDateTime.now());
        job.setStatus("PENDING");
        petIngestionJobCache.put(newId, job);
        processPetIngestionJob(job);
        log.info("Created PetIngestionJob with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/jobs/pet-ingestion/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        PetIngestionJob job = petIngestionJobCache.get(id);
        if (job == null) {
            log.error("PetIngestionJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // -------- Pet Endpoints --------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data");
        }
        String newId = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(newId);
        pet.setPetId(newId);
        pet.setStatus("NEW");
        petCache.put(newId, pet);
        processPet(pet);
        log.info("Created Pet with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // -------- AdoptionRequest Endpoints --------

    @PostMapping("/adoption-requests")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest request) {
        if (request == null || !request.isValid()) {
            log.error("Invalid AdoptionRequest creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid adoption request data");
        }
        if (!petCache.containsKey(request.getPetId())) {
            log.error("Referenced Pet not found with ID: {}", request.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced Pet does not exist");
        }
        Pet pet = petCache.get(request.getPetId());
        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            log.error("Pet with ID {} is not available for adoption", request.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet is not available for adoption");
        }
        String newId = "req-" + adoptionRequestIdCounter.getAndIncrement();
        request.setId(newId);
        request.setRequestId(newId);
        request.setRequestDate(java.time.LocalDateTime.now());
        request.setStatus("PENDING");
        adoptionRequestCache.put(newId, request);
        processAdoptionRequest(request);
        log.info("Created AdoptionRequest with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/adoption-requests/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        AdoptionRequest req = adoptionRequestCache.get(id);
        if (req == null) {
            log.error("AdoptionRequest not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(req);
    }

    // -------- Process Methods --------

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with ID: {}", job.getId());

        // Validate source
        if (job.getSource() == null || job.getSource().isBlank()) {
            log.error("PetIngestionJob {} has invalid source", job.getId());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("PROCESSING");
        try {
            // Simulate fetching pets from external Petstore API
            // For prototype, create a sample pet
            Pet newPet = new Pet();
            String petId = "pet-" + petIdCounter.getAndIncrement();
            newPet.setId(petId);
            newPet.setPetId(petId);
            newPet.setName("Sample Pet");
            newPet.setCategory("cat");
            newPet.setBreed("Siamese");
            newPet.setAge(1);
            newPet.setStatus("NEW");
            petCache.put(petId, newPet);
            processPet(newPet);

            job.setStatus("COMPLETED");
            log.info("PetIngestionJob {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("PetIngestionJob {} failed processing: {}", job.getId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getCategory() == null || pet.getCategory().isBlank() ||
            pet.getBreed() == null || pet.getBreed().isBlank() ||
            pet.getAge() == null) {
            log.error("Pet {} has invalid fields", pet.getId());
            return;
        }

        // Enrich pet data - example: no enrichment here, but could add age category etc.
        pet.setStatus("AVAILABLE");
        petCache.put(pet.getId(), pet);

        log.info("Pet {} is now AVAILABLE", pet.getId());
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        log.info("Processing AdoptionRequest with ID: {}", request.getId());

        Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            log.error("AdoptionRequest {} references unknown Pet ID: {}", request.getId(), request.getPetId());
            request.setStatus("REJECTED");
            adoptionRequestCache.put(request.getId(), request);
            return;
        }

        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            log.error("AdoptionRequest {} rejected because Pet {} status is {}", request.getId(), pet.getId(), pet.getStatus());
            request.setStatus("REJECTED");
            adoptionRequestCache.put(request.getId(), request);
            return;
        }

        // Business logic example: approve adoption if requester name is not blank
        if (request.getRequesterName() != null && !request.getRequesterName().isBlank()) {
            request.setStatus("APPROVED");
            pet.setStatus("ADOPTED");
            petCache.put(pet.getId(), pet);
            log.info("AdoptionRequest {} approved, Pet {} adopted", request.getId(), pet.getId());
        } else {
            request.setStatus("REJECTED");
            log.info("AdoptionRequest {} rejected due to invalid requester name", request.getId());
        }
        adoptionRequestCache.put(request.getId(), request);
    }

}