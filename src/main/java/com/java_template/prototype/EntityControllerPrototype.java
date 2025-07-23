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
import com.java_template.application.entity.AdoptionRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // --- PetJob Endpoints ---

    @PostMapping("/petjobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob.getPetType() == null || petJob.getPetType().isBlank()) {
            log.error("PetJob creation failed: petType is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'petType' is required");
        }
        String id = "job-" + petJobIdCounter.getAndIncrement();
        petJob.setJobId(id);
        petJob.setStatus("PENDING");
        petJob.setRequestedAt(java.time.LocalDateTime.now());
        petJobCache.put(id, petJob);
        processPetJob(petJob);
        log.info("Created PetJob with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", id, "status", petJob.getStatus()));
    }

    @GetMapping("/petjobs/{jobId}")
    public ResponseEntity<?> getPetJob(@PathVariable String jobId) {
        PetJob petJob = petJobCache.get(jobId);
        if (petJob == null) {
            log.error("PetJob not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // --- Pet Endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            log.error("Pet creation failed: petId is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'petId' is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: name is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'name' is required");
        }
        if (pet.getType() == null || pet.getType().isBlank()) {
            log.error("Pet creation failed: type is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'type' is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet creation failed: status is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'status' is required");
        }
        petCache.put(pet.getPetId(), pet);
        processPet(pet);
        log.info("Created Pet with ID: {}", pet.getPetId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{petId}")
    public ResponseEntity<?> getPet(@PathVariable String petId) {
        Pet pet = petCache.get(petId);
        if (pet == null) {
            log.error("Pet not found: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // --- AdoptionRequest Endpoints ---

    @PostMapping("/adoptionrequests")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) {
        if (adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isBlank()) {
            log.error("AdoptionRequest creation failed: petId is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'petId' is required");
        }
        if (adoptionRequest.getRequesterName() == null || adoptionRequest.getRequesterName().isBlank()) {
            log.error("AdoptionRequest creation failed: requesterName is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'requesterName' is required");
        }
        String id = "req-" + adoptionRequestIdCounter.getAndIncrement();
        adoptionRequest.setRequestId(id);
        adoptionRequest.setStatus("PENDING");
        adoptionRequest.setRequestedAt(java.time.LocalDateTime.now());
        adoptionRequestCache.put(id, adoptionRequest);
        processAdoptionRequest(adoptionRequest);
        log.info("Created AdoptionRequest with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("requestId", id, "status", adoptionRequest.getStatus()));
    }

    @GetMapping("/adoptionrequests/{requestId}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String requestId) {
        AdoptionRequest adoptionRequest = adoptionRequestCache.get(requestId);
        if (adoptionRequest == null) {
            log.error("AdoptionRequest not found: {}", requestId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(adoptionRequest);
    }

    // --- Process Methods ---

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getJobId());
        // Validate petType
        if (petJob.getPetType() == null || petJob.getPetType().isBlank()) {
            log.error("Invalid petType in PetJob: {}", petJob.getPetType());
            petJob.setStatus("FAILED");
            return;
        }
        petJob.setStatus("PROCESSING");
        // Simulate fetching pets from Petstore API filtered by petType
        // For prototype, just create a dummy Pet entity
        String dummyPetId = "pet-" + petIdCounter.getAndIncrement();
        Pet pet = new Pet();
        pet.setPetId(dummyPetId);
        pet.setName("DummyPet_" + dummyPetId);
        pet.setType(petJob.getPetType());
        pet.setStatus("ACTIVE");
        petCache.put(dummyPetId, pet);
        log.info("PetJob processed: Created dummy Pet with ID {}", dummyPetId);
        petJob.setStatus("COMPLETED");
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getPetId());
        // Validate pet data completeness
        if (pet.getPetId() == null || pet.getPetId().isBlank() ||
            pet.getName() == null || pet.getName().isBlank() ||
            pet.getType() == null || pet.getType().isBlank() ||
            pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet entity validation failed for ID: {}", pet.getPetId());
            return;
        }
        // Enrich with fun facts (prototype static example)
        log.info("Enriched Pet {} with fun facts", pet.getPetId());
        // Confirm pet record is stored and ready
    }

    private void processAdoptionRequest(AdoptionRequest adoptionRequest) {
        log.info("Processing AdoptionRequest with ID: {}", adoptionRequest.getRequestId());
        // Validate pet availability
        Pet pet = petCache.get(adoptionRequest.getPetId());
        if (pet == null || !"ACTIVE".equalsIgnoreCase(pet.getStatus())) {
            log.error("Pet not available for AdoptionRequest ID: {}", adoptionRequest.getRequestId());
            adoptionRequest.setStatus("REJECTED");
        } else {
            adoptionRequest.setStatus("APPROVED");
        }
        // Notify requester (prototype log)
        log.info("AdoptionRequest {} status set to {}", adoptionRequest.getRequestId(), adoptionRequest.getStatus());
    }
}