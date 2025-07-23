package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PetCreationJob;
import com.java_template.application.entity.Pet;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetCreationJob> petCreationJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petCreationJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/petCreationJob - create new PetCreationJob and trigger processing
    @PostMapping("/petCreationJob")
    public ResponseEntity<?> createPetCreationJob(@RequestBody PetCreationJob petCreationJob) {
        if (petCreationJob == null || petCreationJob.getPetData() == null || petCreationJob.getPetData().isEmpty()) {
            log.error("Invalid petData in PetCreationJob creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid petData in request");
        }
        String newId = String.valueOf(petCreationJobIdCounter.getAndIncrement());
        petCreationJob.setId(newId);
        petCreationJob.setStatus("PENDING");
        petCreationJobCache.put(newId, petCreationJob);

        log.info("PetCreationJob created with ID: {}", newId);
        processPetCreationJob(petCreationJob);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", newId);
        response.put("status", petCreationJob.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/petCreationJob/{id} - retrieve PetCreationJob status
    @GetMapping("/petCreationJob/{id}")
    public ResponseEntity<?> getPetCreationJob(@PathVariable String id) {
        PetCreationJob job = petCreationJobCache.get(id);
        if (job == null) {
            log.error("PetCreationJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetCreationJob not found");
        }
        Map<String, String> response = new HashMap<>();
        response.put("jobId", job.getId());
        response.put("status", job.getStatus());
        return ResponseEntity.ok(response);
    }

    // POST /prototype/pet - create new Pet and trigger processing (for testing or direct pet creation)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Invalid pet data in Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data in request");
        }
        String newId = String.valueOf(petIdCounter.getAndIncrement());
        pet.setPetId(Long.parseLong(newId));
        pet.setId(newId);
        pet.setStatus("AVAILABLE");
        petCache.put(newId, pet);

        log.info("Pet created with petId: {}", newId);
        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - retrieve Pet details
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with petId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // Optional: POST /prototype/pet/{id}/update - create new Pet version (avoided unless necessary)
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            log.error("Pet not found for update with petId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        if (petUpdate == null || petUpdate.getName() == null || petUpdate.getName().isBlank() || petUpdate.getCategory() == null || petUpdate.getCategory().isBlank()) {
            log.error("Invalid pet data in update request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data in request");
        }
        // Create new Pet version with new ID
        String newId = String.valueOf(petIdCounter.getAndIncrement());
        Pet newPetVersion = new Pet();
        newPetVersion.setPetId(Long.parseLong(newId));
        newPetVersion.setId(newId);
        newPetVersion.setName(petUpdate.getName());
        newPetVersion.setCategory(petUpdate.getCategory());
        newPetVersion.setPhotoUrls(petUpdate.getPhotoUrls());
        newPetVersion.setTags(petUpdate.getTags());
        newPetVersion.setStatus(petUpdate.getStatus() != null ? petUpdate.getStatus() : "AVAILABLE");

        petCache.put(newId, newPetVersion);

        log.info("Created new Pet version with petId: {}", newId);
        processPet(newPetVersion);

        return ResponseEntity.status(HttpStatus.CREATED).body(newPetVersion);
    }

    // Optional: POST /prototype/pet/{id}/deactivate - create deactivation record (avoided unless necessary)
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found for deactivation with petId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        // Create a new Pet record with status "DEACTIVATED"
        String newId = String.valueOf(petIdCounter.getAndIncrement());
        Pet deactivatedPet = new Pet();
        deactivatedPet.setPetId(Long.parseLong(newId));
        deactivatedPet.setId(newId);
        deactivatedPet.setName(pet.getName());
        deactivatedPet.setCategory(pet.getCategory());
        deactivatedPet.setPhotoUrls(pet.getPhotoUrls());
        deactivatedPet.setTags(pet.getTags());
        deactivatedPet.setStatus("DEACTIVATED");

        petCache.put(newId, deactivatedPet);

        log.info("Pet deactivated with new petId: {}", newId);
        return ResponseEntity.ok("Pet deactivation recorded");
    }

    private void processPetCreationJob(PetCreationJob job) {
        log.info("Processing PetCreationJob with ID: {}", job.getId());
        // Validate petData JSON structure - basic check for required fields
        Map<String, Object> petData = job.getPetData();
        if (petData == null || !petData.containsKey("name") || !petData.containsKey("category")) {
            log.error("PetCreationJob {} validation failed: missing required petData fields", job.getId());
            job.setStatus("FAILED");
            return;
        }
        try {
            // Map petData to Pet entity fields
            Pet pet = new Pet();
            String petIdStr = String.valueOf(petIdCounter.getAndIncrement());
            pet.setPetId(Long.parseLong(petIdStr));
            pet.setId(petIdStr);
            pet.setName((String) petData.get("name"));
            pet.setCategory((String) petData.get("category"));
            Object photosObj = petData.get("photoUrls");
            if (photosObj instanceof List) {
                pet.setPhotoUrls((List<String>) photosObj);
            }
            Object tagsObj = petData.get("tags");
            if (tagsObj instanceof List) {
                pet.setTags((List<String>) tagsObj);
            }
            String status = (String) petData.getOrDefault("status", "AVAILABLE");
            pet.setStatus(status);

            // Save new Pet entity immutably
            petCache.put(petIdStr, pet);
            log.info("Pet entity created with petId: {} from PetCreationJob: {}", petIdStr, job.getId());

            // Update job status to COMPLETED
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Error processing PetCreationJob {}: {}", job.getId(), e.getMessage());
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Example business logic: could notify inventory or adoption services
        // For prototype, just log processing
        log.info("Pet {} processed with status: {}", pet.getId(), pet.getStatus());
    }
}