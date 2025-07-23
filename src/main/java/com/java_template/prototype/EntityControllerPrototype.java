package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counters for PetIngestionJob
    private final ConcurrentHashMap<String, PetIngestionJob> petIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petIngestionJobIdCounter = new AtomicLong(1);

    // Cache and ID counters for Pet
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // ---------------- PetIngestionJob Endpoints ----------------

    @PostMapping("/jobs/pet-ingest")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob jobRequest) {
        if (jobRequest == null || jobRequest.getSourceUrl() == null || jobRequest.getSourceUrl().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing or blank sourceUrl");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
        }

        String id = String.valueOf(petIngestionJobIdCounter.getAndIncrement());
        PetIngestionJob job = new PetIngestionJob();
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        job.setCreatedAt(java.time.LocalDateTime.now());
        job.setSourceUrl(jobRequest.getSourceUrl());
        job.setStatus(JobStatusEnum.PENDING);

        petIngestionJobCache.put(id, job);
        processPetIngestionJob(job);

        log.info("Created PetIngestionJob with id {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/jobs/pet-ingest/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        PetIngestionJob job = petIngestionJobCache.get(id);
        if (job == null) {
            log.error("PetIngestionJob with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with ID: {}", job.getId());
        job.setStatus(JobStatusEnum.PROCESSING);
        try {
            // Validate source URL (basic check)
            if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("Invalid sourceUrl");
            }
            // Simulate fetching data from Petstore API and ingesting pets
            // For prototype: create dummy Pet entities based on ingestion
            for (int i = 0; i < 3; i++) {
                Pet pet = new Pet();
                String petId = String.valueOf(petIdCounter.getAndIncrement());
                pet.setId(petId);
                pet.setTechnicalId(UUID.randomUUID());
                pet.setName("Pet" + petId);
                pet.setCategory("cat");
                pet.setPhotoUrls(List.of("http://example.com/pet" + petId + ".jpg"));
                pet.setTags(List.of("imported"));
                pet.setStatus(PetStatusEnum.AVAILABLE);
                petCache.put(petId, pet);
                processPet(pet);
            }
            job.setStatus(JobStatusEnum.COMPLETED);
            log.info("PetIngestionJob {} completed successfully", job.getId());
        } catch (Exception ex) {
            job.setStatus(JobStatusEnum.FAILED);
            log.error("PetIngestionJob {} failed: {}", job.getId(), ex.getMessage());
        }
    }

    // ---------------- Pet Endpoints ----------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet petRequest) {
        if (petRequest == null || petRequest.getName() == null || petRequest.getName().isBlank()) {
            log.error("Invalid Pet creation request: missing or blank name");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (petRequest.getCategory() == null || petRequest.getCategory().isBlank()) {
            log.error("Invalid Pet creation request: missing or blank category");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }

        String id = String.valueOf(petIdCounter.getAndIncrement());
        Pet pet = new Pet();
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());
        pet.setName(petRequest.getName());
        pet.setCategory(petRequest.getCategory());
        pet.setPhotoUrls(petRequest.getPhotoUrls() != null ? petRequest.getPhotoUrls() : new ArrayList<>());
        pet.setTags(petRequest.getTags() != null ? petRequest.getTags() : new ArrayList<>());
        pet.setStatus(PetStatusEnum.AVAILABLE);

        petCache.put(id, pet);
        processPet(pet);

        log.info("Created Pet with id {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate pet data integrity
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet with ID {} has invalid name", pet.getId());
            return;
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet with ID {} has invalid category", pet.getId());
            return;
        }
        // No heavy processing needed for prototype
        log.info("Pet with ID {} is AVAILABLE and ready for retrieval", pet.getId());
    }

    // ----------- Enum definitions -----------

    public enum JobStatusEnum {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public enum PetStatusEnum {
        AVAILABLE,
        PENDING_ADOPTION,
        ADOPTED
    }
}