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

    // Cache and counters for PetAdoptionJob entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.PetAdoptionJob> petAdoptionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petAdoptionJobIdCounter = new AtomicLong(1);

    // Cache and counters for Pet entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and counters for AdoptionRequest entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // POST /prototype/petAdoptionJob - create PetAdoptionJob
    @PostMapping("/petAdoptionJob")
    public ResponseEntity<?> createPetAdoptionJob(@RequestBody com.java_template.application.entity.PetAdoptionJob job) {
        if (job == null) {
            log.error("PetAdoptionJob payload is null");
            return ResponseEntity.badRequest().body("PetAdoptionJob payload cannot be null");
        }
        // Generate business ID if missing
        if (job.getId() == null || job.getId().isBlank()) {
            job.setId("job-" + petAdoptionJobIdCounter.getAndIncrement());
        }
        // Default status to PENDING if not set
        if (job.getStatus() == null) {
            job.setStatus(com.java_template.application.entity.JobStatusEnum.PENDING);
        }
        if (!job.isValid()) {
            log.error("Invalid PetAdoptionJob data: {}", job);
            return ResponseEntity.badRequest().body("Invalid PetAdoptionJob data");
        }
        petAdoptionJobCache.put(job.getId(), job);
        processPetAdoptionJob(job);
        log.info("Created PetAdoptionJob with ID {}", job.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /prototype/petAdoptionJob/{id} - retrieve PetAdoptionJob
    @GetMapping("/petAdoptionJob/{id}")
    public ResponseEntity<?> getPetAdoptionJob(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }
        com.java_template.application.entity.PetAdoptionJob job = petAdoptionJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetAdoptionJob not found for ID: " + id);
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet - create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody com.java_template.application.entity.Pet pet) {
        if (pet == null) {
            log.error("Pet payload is null");
            return ResponseEntity.badRequest().body("Pet payload cannot be null");
        }
        if (pet.getId() == null || pet.getId().isBlank()) {
            pet.setId("pet-" + petIdCounter.getAndIncrement());
        }
        if (pet.getStatus() == null) {
            pet.setStatus(com.java_template.application.entity.PetStatusEnum.AVAILABLE);
        }
        if (!pet.isValid()) {
            log.error("Invalid Pet data: {}", pet);
            return ResponseEntity.badRequest().body("Invalid Pet data");
        }
        petCache.put(pet.getId(), pet);
        processPet(pet);
        log.info("Created Pet with ID {}", pet.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - retrieve Pet
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }
        com.java_template.application.entity.Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found for ID: " + id);
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/adoptionRequest - create AdoptionRequest
    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody com.java_template.application.entity.AdoptionRequest request) {
        if (request == null) {
            log.error("AdoptionRequest payload is null");
            return ResponseEntity.badRequest().body("AdoptionRequest payload cannot be null");
        }
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId("request-" + adoptionRequestIdCounter.getAndIncrement());
        }
        if (request.getStatus() == null) {
            request.setStatus(com.java_template.application.entity.RequestStatusEnum.PENDING);
        }
        if (!request.isValid()) {
            log.error("Invalid AdoptionRequest data: {}", request);
            return ResponseEntity.badRequest().body("Invalid AdoptionRequest data");
        }
        adoptionRequestCache.put(request.getId(), request);
        processAdoptionRequest(request);
        log.info("Created AdoptionRequest with ID {}", request.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    // GET /prototype/adoptionRequest/{id} - retrieve AdoptionRequest
    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }
        com.java_template.application.entity.AdoptionRequest request = adoptionRequestCache.get(id);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found for ID: " + id);
        }
        return ResponseEntity.ok(request);
    }

    // Processing methods with real business logic

    private void processPetAdoptionJob(com.java_template.application.entity.PetAdoptionJob job) {
        log.info("Processing PetAdoptionJob with ID: {}", job.getId());

        // Validate pet availability
        com.java_template.application.entity.Pet pet = petCache.get(job.getPetId());
        if (pet == null) {
            log.error("Pet with ID {} not found", job.getPetId());
            job.setStatus(com.java_template.application.entity.JobStatusEnum.FAILED);
            petAdoptionJobCache.put(job.getId(), job);
            return;
        }
        if (pet.getStatus() != com.java_template.application.entity.PetStatusEnum.AVAILABLE) {
            log.error("Pet with ID {} is not available for adoption", pet.getId());
            job.setStatus(com.java_template.application.entity.JobStatusEnum.FAILED);
            petAdoptionJobCache.put(job.getId(), job);
            return;
        }

        // Create AdoptionRequest entity
        com.java_template.application.entity.AdoptionRequest adoptionRequest = new com.java_template.application.entity.AdoptionRequest();
        adoptionRequest.setId("req-" + adoptionRequestIdCounter.getAndIncrement());
        adoptionRequest.setPetId(pet.getId());
        adoptionRequest.setRequesterName(job.getAdopterName());
        adoptionRequest.setRequestDate(new Date());
        adoptionRequest.setStatus(com.java_template.application.entity.RequestStatusEnum.PENDING);
        adoptionRequestCache.put(adoptionRequest.getId(), adoptionRequest);

        // Update pet status to ADOPTED
        pet.setStatus(com.java_template.application.entity.PetStatusEnum.ADOPTED);
        petCache.put(pet.getId(), pet);

        // Update job status to COMPLETED
        job.setStatus(com.java_template.application.entity.JobStatusEnum.COMPLETED);
        petAdoptionJobCache.put(job.getId(), job);

        log.info("PetAdoptionJob {} processed successfully", job.getId());
    }

    private void processPet(com.java_template.application.entity.Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Check mandatory fields
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet name is mandatory");
            return;
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet category is mandatory");
            return;
        }
        // Pet status already set to AVAILABLE in createPet()

        log.info("Pet {} is ready for adoption", pet.getId());
    }

    private void processAdoptionRequest(com.java_template.application.entity.AdoptionRequest request) {
        log.info("Processing AdoptionRequest with ID: {}", request.getId());

        // Validate pet availability
        com.java_template.application.entity.Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            log.error("Pet with ID {} not found for adoption request", request.getPetId());
            request.setStatus(com.java_template.application.entity.RequestStatusEnum.REJECTED);
            adoptionRequestCache.put(request.getId(), request);
            return;
        }
        if (pet.getStatus() != com.java_template.application.entity.PetStatusEnum.AVAILABLE) {
            log.error("Pet with ID {} is not available for adoption in request", pet.getId());
            request.setStatus(com.java_template.application.entity.RequestStatusEnum.REJECTED);
            adoptionRequestCache.put(request.getId(), request);
            return;
        }

        // For simplicity approve all valid requests
        request.setStatus(com.java_template.application.entity.RequestStatusEnum.APPROVED);
        adoptionRequestCache.put(request.getId(), request);

        log.info("AdoptionRequest {} approved", request.getId());

        // Notification logic could be added here
    }
}