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
import com.java_template.application.entity.Favorite;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counters for PurrfectPetsJob
    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    // Cache and ID counters for Pet
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and ID counters for Favorite
    private final ConcurrentHashMap<String, Favorite> favoriteCache = new ConcurrentHashMap<>();
    private final AtomicLong favoriteIdCounter = new AtomicLong(1);

    // ----------------------- PurrfectPetsJob Endpoints -----------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        if (job == null) {
            log.error("Received null PurrfectPetsJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PurrfectPetsJob data is required");
        }
        if (job.getOperationType() == null || job.getOperationType().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("OperationType is required");
        }
        // Generate business id
        String jobId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setId(jobId);
        job.setStatus("PENDING");
        jobCachePutAndProcess(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", job.getId(), "status", job.getStatus()));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PurrfectPetsJob not found with id " + id);
        }
        return ResponseEntity.ok(job);
    }

    private void jobCachePutAndProcess(PurrfectPetsJob job) {
        purrfectPetsJobCache.put(job.getId(), job);
        processPurrfectPetsJob(job);
    }

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());

        // Validation
        if (job.getOperationType() == null || job.getOperationType().isBlank()) {
            log.error("Invalid operationType in job {}", job.getId());
            job.setStatus("FAILED");
            return;
        }

        try {
            job.setStatus("PROCESSING");
            // Simulated execution logic
            switch (job.getOperationType()) {
                case "ImportPets":
                    // Simulate fetching and importing pets from Petstore API
                    // Here, just log and pretend to create a pet
                    String petId = "pet-" + petIdCounter.getAndIncrement();
                    Pet newPet = new Pet();
                    newPet.setId(petId);
                    newPet.setName("ImportedPet-" + petId);
                    newPet.setCategory("cat");
                    newPet.setStatus("AVAILABLE");
                    petCache.put(newPet.getId(), newPet);
                    processPet(newPet);
                    log.info("Imported pet with ID: {}", petId);
                    break;
                case "SyncFavorites":
                    // Simulate syncing favorites - no real external call here
                    log.info("SyncFavorites operation executed for job {}", job.getId());
                    break;
                default:
                    log.error("Unknown operationType {} for job {}", job.getOperationType(), job.getId());
                    job.setStatus("FAILED");
                    return;
            }
            job.setStatus("COMPLETED");
            log.info("PurrfectPetsJob {} completed successfully", job.getId());
        } catch (Exception e) {
            log.error("Processing job {} failed: {}", job.getId(), e.getMessage());
            job.setStatus("FAILED");
        }
    }

    // ----------------------- Pet Endpoints -----------------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet status is required");
        }
        String petId = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(petId);
        petCache.put(pet.getId(), pet);
        processPet(pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("petId", pet.getId(), "status", pet.getStatus()));
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found with id " + id);
        }
        return ResponseEntity.ok(pet);
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate category and status are non-blank (already validated in controller)
        // Simulate updating internal caches or indexes if needed
        log.info("Pet {} processed: name={}, category={}, status={}", pet.getId(), pet.getName(), pet.getCategory(), pet.getStatus());
    }

    // ----------------------- Favorite Endpoints -----------------------

    @PostMapping("/favorites")
    public ResponseEntity<?> createFavorite(@RequestBody Favorite favorite) {
        if (favorite == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Favorite data is required");
        }
        if (favorite.getUserId() == null || favorite.getUserId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("UserId is required");
        }
        if (favorite.getPetId() == null || favorite.getPetId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetId is required");
        }
        if (favorite.getStatus() == null || favorite.getStatus().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Favorite status is required");
        }
        String favoriteId = "fav-" + favoriteIdCounter.getAndIncrement();
        favorite.setId(favoriteId);
        favoriteCache.put(favorite.getId(), favorite);
        processFavorite(favorite);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("favoriteId", favorite.getId(), "status", favorite.getStatus()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavoritesByUser(@RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("UserId query parameter is required");
        }
        List<Favorite> results = new ArrayList<>();
        for (Favorite fav : favoriteCache.values()) {
            if (userId.equals(fav.getUserId()) && "ACTIVE".equals(fav.getStatus())) {
                results.add(fav);
            }
        }
        return ResponseEntity.ok(results);
    }

    private void processFavorite(Favorite favorite) {
        log.info("Processing Favorite with ID: {}", favorite.getId());

        // Validate that userId and petId exist in caches - simulate check
        if (!petCache.containsKey(favorite.getPetId())) {
            log.error("Favorite processing failed: Pet ID {} does not exist", favorite.getPetId());
            return;
        }

        // Update user favorite list - simulated by caching favorite entity
        log.info("Favorite {} processed: userId={}, petId={}, status={}", favorite.getId(), favorite.getUserId(), favorite.getPetId(), favorite.getStatus());
    }

}