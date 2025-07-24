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
import com.java_template.application.entity.PetStatusUpdate;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetIngestionJob> petIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petIngestionJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, PetStatusUpdate> petStatusUpdateCache = new ConcurrentHashMap<>();
    private final AtomicLong petStatusUpdateIdCounter = new AtomicLong(1);

    // POST /prototype/petIngestionJob - create PetIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) {
        if (job.getSource() == null || job.getSource().isBlank()) {
            log.error("PetIngestionJob creation failed: source is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("source is required");
        }
        String id = String.valueOf(petIngestionJobIdCounter.getAndIncrement());
        job.setId(id);
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());
        petIngestionJobCache.put(id, job);
        processPetIngestionJob(job);
        log.info("Created PetIngestionJob with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /prototype/petIngestionJob/{id} - retrieve PetIngestionJob
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        PetIngestionJob job = petIngestionJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet - create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: name is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet creation failed: category is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("category is required");
        }
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new ArrayList<>());
        }
        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        petCache.put(id, pet);
        processPet(pet);
        log.info("Created Pet with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - retrieve Pet
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/petStatusUpdate - create PetStatusUpdate
    @PostMapping("/petStatusUpdate")
    public ResponseEntity<?> createPetStatusUpdate(@RequestBody PetStatusUpdate update) {
        if (update.getPetId() == null || update.getPetId().isBlank()) {
            log.error("PetStatusUpdate creation failed: petId is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required");
        }
        if (update.getNewStatus() == null || update.getNewStatus().isBlank()) {
            log.error("PetStatusUpdate creation failed: newStatus is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("newStatus is required");
        }
        if (!petCache.containsKey(update.getPetId())) {
            log.error("PetStatusUpdate creation failed: petId {} does not exist", update.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId does not exist");
        }
        String id = String.valueOf(petStatusUpdateIdCounter.getAndIncrement());
        update.setId(id);
        update.setStatus("PENDING");
        update.setUpdatedAt(new Date());
        petStatusUpdateCache.put(id, update);
        processPetStatusUpdate(update);
        log.info("Created PetStatusUpdate with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(update);
    }

    // GET /prototype/petStatusUpdate/{id} - retrieve PetStatusUpdate
    @GetMapping("/petStatusUpdate/{id}")
    public ResponseEntity<?> getPetStatusUpdate(@PathVariable String id) {
        PetStatusUpdate update = petStatusUpdateCache.get(id);
        if (update == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetStatusUpdate not found");
        }
        return ResponseEntity.ok(update);
    }

    // Process methods with real business logic

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with ID: {}", job.getId());
        // 1. Update status to PROCESSING
        job.setStatus("PROCESSING");
        petIngestionJobCache.put(job.getId(), job);

        try {
            // 2. Simulate fetching pet data from external Petstore API
            // Here, for prototype, we simulate adding a pet
            Pet newPet = new Pet();
            newPet.setId(String.valueOf(petIdCounter.getAndIncrement()));
            newPet.setName("Simulated Pet");
            newPet.setCategory("cat");
            newPet.setPhotoUrls(Collections.emptyList());
            newPet.setTags(Collections.singletonList("simulated"));
            newPet.setStatus("AVAILABLE");
            petCache.put(newPet.getId(), newPet);
            processPet(newPet);

            // 3. Update job status to COMPLETED
            job.setStatus("COMPLETED");
            petIngestionJobCache.put(job.getId(), job);
            log.info("PetIngestionJob {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            petIngestionJobCache.put(job.getId(), job);
            log.error("PetIngestionJob {} failed: {}", job.getId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validation already done in controller
        // Enrichment example: add a tag "processed"
        if (!pet.getTags().contains("processed")) {
            pet.getTags().add("processed");
        }
        petCache.put(pet.getId(), pet);
        log.info("Pet {} processed and enriched", pet.getId());
    }

    private void processPetStatusUpdate(PetStatusUpdate update) {
        log.info("Processing PetStatusUpdate with ID: {}", update.getId());
        // Validate pet exists
        Pet pet = petCache.get(update.getPetId());
        if (pet == null) {
            log.error("PetStatusUpdate processing failed: Pet {} not found", update.getPetId());
            update.setStatus("FAILED");
            petStatusUpdateCache.put(update.getId(), update);
            return;
        }
        // Create new Pet entity representing status update (immutable)
        Pet updatedPet = new Pet();
        updatedPet.setId(String.valueOf(petIdCounter.getAndIncrement()));
        updatedPet.setName(pet.getName());
        updatedPet.setCategory(pet.getCategory());
        updatedPet.setPhotoUrls(new ArrayList<>(pet.getPhotoUrls()));
        updatedPet.setTags(new ArrayList<>(pet.getTags()));
        updatedPet.setStatus(update.getNewStatus());
        petCache.put(updatedPet.getId(), updatedPet);

        // Mark PetStatusUpdate as PROCESSED
        update.setStatus("PROCESSED");
        petStatusUpdateCache.put(update.getId(), update);
        log.info("PetStatusUpdate {} processed: Pet {} status updated to {}", update.getId(), updatedPet.getId(), update.getNewStatus());
    }
}