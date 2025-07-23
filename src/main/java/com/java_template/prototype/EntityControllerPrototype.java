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

    private final ConcurrentHashMap<String, com.java_template.application.entity.PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, com.java_template.application.entity.AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // ============================
    // PetJob Endpoints
    // ============================

    @PostMapping("/petjobs")
    public ResponseEntity<?> createPetJob(@RequestBody com.java_template.application.entity.PetJob petJob) {
        if (petJob == null) {
            log.error("PetJob request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetJob cannot be null");
        }
        // Generate business id
        petJob.setId("PJ-" + petJobIdCounter.getAndIncrement());
        petJob.setTechnicalId(UUID.randomUUID());
        petJob.setCreatedAt(java.time.LocalDateTime.now());
        if (petJob.getStatus() == null || petJob.getStatus().isBlank()) {
            petJob.setStatus("PENDING");
        }

        if (!petJob.isValid()) {
            log.error("Invalid PetJob data: {}", petJob);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data");
        }

        petJobCache.put(petJob.getId(), petJob);
        processPetJob(petJob);
        log.info("PetJob created with ID: {}", petJob.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/petjobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        com.java_template.application.entity.PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    private void processPetJob(com.java_template.application.entity.PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        // Validation
        if (petJob.getPetType() == null || petJob.getPetType().isBlank()) {
            log.error("PetJob petType is invalid");
            petJob.setStatus("FAILED");
            return;
        }
        if (petJob.getOperation() == null || petJob.getOperation().isBlank()) {
            log.error("PetJob operation is invalid");
            petJob.setStatus("FAILED");
            return;
        }

        // Dispatch business logic based on operation
        try {
            if ("ingest".equalsIgnoreCase(petJob.getOperation())) {
                // Example: ingest pets of given type (simulate)
                log.info("Ingesting pets of type: {}", petJob.getPetType());
                // Here we might trigger creation of Pet entities or call external APIs
            } else if ("updateStatus".equalsIgnoreCase(petJob.getOperation())) {
                log.info("Updating status for pets of type: {}", petJob.getPetType());
                // Example update status logic (not implemented in prototype)
            } else {
                log.warn("Unknown operation: {}", petJob.getOperation());
                petJob.setStatus("FAILED");
                return;
            }
            petJob.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Error processing PetJob with ID: {}", petJob.getId(), e);
            petJob.setStatus("FAILED");
        }
    }

    // ============================
    // Pet Endpoints
    // ============================

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody com.java_template.application.entity.Pet pet) {
        if (pet == null) {
            log.error("Pet request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet cannot be null");
        }
        pet.setId("P-" + petIdCounter.getAndIncrement());
        pet.setTechnicalId(UUID.randomUUID());
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("CREATED");
        }

        if (!pet.isValid()) {
            log.error("Invalid Pet data: {}", pet);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }

        petCache.put(pet.getId(), pet);
        processPet(pet);
        log.info("Pet created with ID: {}", pet.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        com.java_template.application.entity.Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    private void processPet(com.java_template.application.entity.Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate required fields (already done in isValid)
        // Enrich with default adoptionStatus if blank
        if (pet.getAdoptionStatus() == null || pet.getAdoptionStatus().isBlank()) {
            pet.setAdoptionStatus("AVAILABLE");
            log.info("Pet adoptionStatus set to AVAILABLE for ID: {}", pet.getId());
        }

        // Mark as processed
        pet.setStatus("PROCESSED");
    }

    // ============================
    // AdoptionRequest Endpoints
    // ============================

    @PostMapping("/adoptionrequests")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody com.java_template.application.entity.AdoptionRequest adoptionRequest) {
        if (adoptionRequest == null) {
            log.error("AdoptionRequest request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("AdoptionRequest cannot be null");
        }
        adoptionRequest.setId("AR-" + adoptionRequestIdCounter.getAndIncrement());
        adoptionRequest.setTechnicalId(UUID.randomUUID());
        adoptionRequest.setRequestDate(java.time.LocalDateTime.now());
        if (adoptionRequest.getStatus() == null || adoptionRequest.getStatus().isBlank()) {
            adoptionRequest.setStatus("SUBMITTED");
        }

        if (!adoptionRequest.isValid()) {
            log.error("Invalid AdoptionRequest data: {}", adoptionRequest);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid AdoptionRequest data");
        }

        adoptionRequestCache.put(adoptionRequest.getId(), adoptionRequest);
        processAdoptionRequest(adoptionRequest);
        log.info("AdoptionRequest created with ID: {}", adoptionRequest.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(adoptionRequest);
    }

    @GetMapping("/adoptionrequests/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        com.java_template.application.entity.AdoptionRequest adoptionRequest = adoptionRequestCache.get(id);
        if (adoptionRequest == null) {
            log.error("AdoptionRequest not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(adoptionRequest);
    }

    private void processAdoptionRequest(com.java_template.application.entity.AdoptionRequest adoptionRequest) {
        log.info("Processing AdoptionRequest with ID: {}", adoptionRequest.getId());

        // Validate pet availability (simulate check)
        com.java_template.application.entity.Pet pet = petCache.get(adoptionRequest.getPetId());
        if (pet == null) {
            log.error("Pet not found for AdoptionRequest with petId: {}", adoptionRequest.getPetId());
            adoptionRequest.setStatus("REJECTED");
            return;
        }
        if (!"AVAILABLE".equalsIgnoreCase(pet.getAdoptionStatus())) {
            log.info("Pet with ID: {} is not available for adoption", pet.getId());
            adoptionRequest.setStatus("REJECTED");
            return;
        }

        // Approve adoption
        adoptionRequest.setStatus("APPROVED");
        // Update pet adoptionStatus to PENDING (simulate immutability by creating new version skipped for prototype)
        pet.setAdoptionStatus("PENDING");
        petCache.put(pet.getId(), pet);
        log.info("AdoptionRequest approved and Pet status updated to PENDING for pet ID: {}", pet.getId());
    }
}