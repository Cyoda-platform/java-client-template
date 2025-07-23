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

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/petJob - Create PetJob entity
    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getType() == null || petJob.getType().isBlank()) {
            log.error("Invalid PetJob: missing type");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: type");
        }
        String id = "job-" + petJobIdCounter.getAndIncrement();
        petJob.setId(id);
        petJob.setStatus("PENDING");
        petJobCache.put(id, petJob);
        log.info("Created PetJob with ID: {}", id);

        try {
            processPetJob(petJob);
        } catch (Exception e) {
            log.error("Error processing PetJob with ID: {}", id, e);
            petJob.setStatus("FAILED");
            petJobCache.put(id, petJob);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process PetJob");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", petJob.getStatus()));
    }

    // GET /prototype/petJob/{id} - Retrieve PetJob by ID
    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // POST /prototype/pet - Create Pet entity
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Invalid Pet: missing name or category");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: name and category");
        }
        String id = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(id);
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }
        petCache.put(id, pet);
        log.info("Created Pet with ID: {}", id);

        try {
            processPet(pet);
        } catch (Exception e) {
            log.error("Error processing Pet with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process Pet");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - Retrieve Pet by ID
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // Business logic for processing PetJob entity
    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());
        if (petJob.getType().equalsIgnoreCase("AddPet")) {
            Map<String, Object> payload = petJob.getPayload();
            if (payload == null) {
                log.error("PetJob payload is null for AddPet");
                petJob.setStatus("FAILED");
                return;
            }
            String name = (String) payload.get("name");
            String category = (String) payload.get("category");
            if (name == null || name.isBlank() || category == null || category.isBlank()) {
                log.error("Invalid Pet data in PetJob payload");
                petJob.setStatus("FAILED");
                return;
            }
            Pet newPet = new Pet();
            newPet.setId("pet-" + petIdCounter.getAndIncrement());
            newPet.setName(name);
            newPet.setCategory(category);
            Object tagsObj = payload.get("tags");
            if (tagsObj instanceof List<?>) {
                List<String> tags = new ArrayList<>();
                for (Object o : (List<?>) tagsObj) {
                    if (o instanceof String) tags.add((String) o);
                }
                newPet.setTags(tags);
            }
            Object photosObj = payload.get("photoUrls");
            if (photosObj instanceof List<?>) {
                List<String> photos = new ArrayList<>();
                for (Object o : (List<?>) photosObj) {
                    if (o instanceof String) photos.add((String) o);
                }
                newPet.setPhotoUrls(photos);
            }
            newPet.setStatus("AVAILABLE");
            petCache.put(newPet.getId(), newPet);
            log.info("Added new Pet with ID: {}", newPet.getId());
            petJob.setStatus("COMPLETED");
        } else if (petJob.getType().equalsIgnoreCase("UpdatePetStatus")) {
            Map<String, Object> payload = petJob.getPayload();
            if (payload == null) {
                log.error("PetJob payload is null for UpdatePetStatus");
                petJob.setStatus("FAILED");
                return;
            }
            String petId = (String) payload.get("id");
            String newStatus = (String) payload.get("status");
            if (petId == null || petId.isBlank() || newStatus == null || newStatus.isBlank()) {
                log.error("Invalid Pet ID or status in PetJob payload");
                petJob.setStatus("FAILED");
                return;
            }
            Pet existingPet = petCache.get(petId);
            if (existingPet == null) {
                log.error("Pet not found for update with ID: {}", petId);
                petJob.setStatus("FAILED");
                return;
            }
            Pet updatedPet = new Pet();
            updatedPet.setId("pet-" + petIdCounter.getAndIncrement());
            updatedPet.setName(existingPet.getName());
            updatedPet.setCategory(existingPet.getCategory());
            updatedPet.setTags(existingPet.getTags());
            updatedPet.setPhotoUrls(existingPet.getPhotoUrls());
            updatedPet.setStatus(newStatus);
            petCache.put(updatedPet.getId(), updatedPet);
            log.info("Updated Pet status for new Pet ID: {}", updatedPet.getId());
            petJob.setStatus("COMPLETED");
        } else {
            log.error("Unknown PetJob type: {}", petJob.getType());
            petJob.setStatus("FAILED");
        }
        petJobCache.put(petJob.getId(), petJob);
    }

    // Business logic for processing Pet entity
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        if (pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet validation failed for ID: {}", pet.getId());
            throw new IllegalArgumentException("Pet must have non-blank name and category");
        }
        // Future scope: indexing, notifications, other processing workflows
        log.info("Pet processing completed for ID: {}", pet.getId());
    }
}