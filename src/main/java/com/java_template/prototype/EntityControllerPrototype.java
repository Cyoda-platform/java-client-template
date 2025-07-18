package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // -------------------- PurrfectPetsJob Endpoints --------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody PurrfectPetsJob job) {
        if (job.getJobType() == null || job.getJobType().isBlank()) {
            log.error("Job creation failed: jobType is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("jobType is required");
        }
        // Generate business ID
        String businessId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setId(businessId);
        job.setJobId(businessId);
        job.setStatus("PENDING");
        job.setCreatedAt(Optional.ofNullable(job.getCreatedAt()).orElse(java.time.LocalDateTime.now()));

        purrfectPetsJobCache.put(businessId, job);

        processPurrfectPetsJob(job);

        log.info("Created PurrfectPetsJob with ID: {}", businessId);
        Map<String, String> response = new HashMap<>();
        response.put("jobId", businessId);
        response.put("status", job.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable String jobId) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(jobId);
        if (job == null) {
            log.error("Job not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());
        // Validate jobType
        if (!job.getJobType().equals("PetDataSync") && !job.getJobType().equals("AdoptionProcessing")) {
            job.setStatus("FAILED");
            log.error("Invalid jobType: {}", job.getJobType());
            return;
        }
        job.setStatus("PROCESSING");

        try {
            if (job.getJobType().equals("PetDataSync")) {
                // Simulate fetching and syncing pet data from Petstore API
                log.info("Syncing pet data from Petstore API...");
                // For prototype, no real API call
            } else if (job.getJobType().equals("AdoptionProcessing")) {
                // Process adoption requests queue
                log.info("Processing adoption requests...");
                // For prototype, no real queue processing
            }
            job.setStatus("COMPLETED");
            log.info("Job {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Job {} failed processing: {}", job.getId(), e.getMessage());
        }
        purrfectPetsJobCache.put(job.getId(), job);
    }

    // -------------------- Pet Endpoints --------------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getBreed() == null || pet.getBreed().isBlank()
                || pet.getAge() == null) {
            log.error("Pet creation failed: Missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing required pet fields: name, species, breed, age");
        }
        String businessId = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(businessId);
        pet.setPetId(businessId);
        pet.setStatus("AVAILABLE");
        petCache.put(businessId, pet);

        processPet(pet);

        log.info("Created Pet with ID: {}", businessId);
        Map<String, String> response = new HashMap<>();
        response.put("petId", businessId);
        response.put("status", pet.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate completeness done in controller
        // Business logic: update search indexes or caches if needed
        // For prototype, just log
        log.info("Pet {} is available for adoption", pet.getName());
    }

    // -------------------- AdoptionRequest Endpoints --------------------

    @PostMapping("/adoption-requests")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest request) {
        if (request.getPetId() == null || request.getPetId().isBlank()
                || request.getAdopterName() == null || request.getAdopterName().isBlank()) {
            log.error("Adoption request creation failed: Missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing required fields: petId, adopterName");
        }
        String businessId = "request-" + adoptionRequestIdCounter.getAndIncrement();
        request.setId(businessId);
        request.setRequestId(businessId);
        request.setStatus("PENDING");
        request.setRequestDate(Optional.ofNullable(request.getRequestDate()).orElse(java.time.LocalDateTime.now()));

        adoptionRequestCache.put(businessId, request);

        processAdoptionRequest(request);

        log.info("Created AdoptionRequest with ID: {}", businessId);
        Map<String, String> response = new HashMap<>();
        response.put("requestId", businessId);
        response.put("status", request.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/adoption-requests/{requestId}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String requestId) {
        AdoptionRequest request = adoptionRequestCache.get(requestId);
        if (request == null) {
            log.error("Adoption request not found: {}", requestId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Adoption request not found");
        }
        return ResponseEntity.ok(request);
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        log.info("Processing AdoptionRequest with ID: {}", request.getId());
        Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            request.setStatus("REJECTED");
            log.error("Adoption request rejected: pet not found {}", request.getPetId());
        } else if (!"AVAILABLE".equals(pet.getStatus())) {
            request.setStatus("REJECTED");
            log.error("Adoption request rejected: pet not available {}", request.getPetId());
        } else {
            request.setStatus("APPROVED");
            pet.setStatus("PENDING"); // or ADOPTED based on business rule
            petCache.put(pet.getId(), pet);
            log.info("Adoption request approved for pet {}", pet.getName());
        }
        adoptionRequestCache.put(request.getId(), request);
    }

}