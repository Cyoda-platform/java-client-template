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

    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Favorite> favoriteCache = new ConcurrentHashMap<>();
    private final AtomicLong favoriteIdCounter = new AtomicLong(1);

    // POST /prototype/purrfectPetsJob
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        try {
            if (job == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
            }
            if (job.getPetType() == null || job.getPetType().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petType is required");
            }
            String id = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
            job.setId(id);
            job.setJobId(id);
            job.setStatus("PENDING");
            if (!job.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid job data");
            }
            purrfectPetsJobCache.put(id, job);
            processPurrfectPetsJob(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(job);
        } catch (Exception e) {
            log.error("Failed to create PurrfectPetsJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /prototype/purrfectPetsJob/{id}
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            if (pet == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
            }
            String id = "pet-" + petIdCounter.getAndIncrement();
            pet.setId(id);
            pet.setPetId(id);
            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                pet.setStatus("AVAILABLE");
            }
            if (!pet.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data");
            }
            petCache.put(id, pet);
            processPet(pet);
            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Failed to create Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /prototype/pet/{id}
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/favorite
    @PostMapping("/favorite")
    public ResponseEntity<?> createFavorite(@RequestBody Favorite favorite) {
        try {
            if (favorite == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
            }
            if (favorite.getUserId() == null || favorite.getUserId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("userId is required");
            }
            if (favorite.getPetId() == null || favorite.getPetId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required");
            }
            String id = "fav-" + favoriteIdCounter.getAndIncrement();
            favorite.setId(id);
            favorite.setFavoriteId(id);
            if (favorite.getStatus() == null || favorite.getStatus().isBlank()) {
                favorite.setStatus("ACTIVE");
            }
            if (!favorite.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid favorite data");
            }
            favoriteCache.put(id, favorite);
            processFavorite(favorite);
            return ResponseEntity.status(HttpStatus.CREATED).body(favorite);
        } catch (Exception e) {
            log.error("Failed to create Favorite", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /prototype/favorite/{id}
    @GetMapping("/favorite/{id}")
    public ResponseEntity<?> getFavorite(@PathVariable String id) {
        Favorite favorite = favoriteCache.get(id);
        if (favorite == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Favorite not found");
        }
        return ResponseEntity.ok(favorite);
    }

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());
        // 1. Validate petType
        if (job.getPetType() == null || job.getPetType().isBlank()) {
            log.error("Job petType is invalid");
            job.setStatus("FAILED");
            return;
        }
        job.setStatus("PROCESSING");

        // 2. Fetch pets from Petstore API filtered by petType (simulate fetch)
        // For prototype, simulate pets creation
        List<Pet> fetchedPets = new ArrayList<>();
        Pet pet1 = new Pet();
        pet1.setPetId("pet-" + petIdCounter.get());
        pet1.setName("Whiskers");
        pet1.setType(job.getPetType());
        pet1.setAge(3);
        pet1.setStatus("AVAILABLE");
        pet1.setId("pet-" + petIdCounter.getAndIncrement());
        petCache.put(pet1.getId(), pet1);
        fetchedPets.add(pet1);

        Pet pet2 = new Pet();
        pet2.setPetId("pet-" + petIdCounter.get());
        pet2.setName("Fido");
        pet2.setType(job.getPetType());
        pet2.setAge(5);
        pet2.setStatus("AVAILABLE");
        pet2.setId("pet-" + petIdCounter.getAndIncrement());
        petCache.put(pet2.getId(), pet2);
        fetchedPets.add(pet2);

        // 3. Save or update pet entities already done above

        // 4. Completion
        job.setStatus("COMPLETED");

        // 5. Notification (log)
        log.info("Completed PurrfectPetsJob with ID: {}. Pets fetched: {}", job.getId(), fetchedPets.size());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate pet details
        if (!pet.isValid()) {
            log.error("Invalid pet data for petId: {}", pet.getPetId());
            return;
        }
        // Business logic: flag pets not available or mark adopted
        if (pet.getStatus().equalsIgnoreCase("ADOPTED")) {
            log.info("Pet {} is adopted.", pet.getName());
        }
        // Persist changes already done by cache
    }

    private void processFavorite(Favorite favorite) {
        log.info("Processing Favorite with ID: {}", favorite.getId());
        // Validate user and pet existence
        if (favorite.getUserId() == null || favorite.getUserId().isBlank()) {
            log.error("Favorite userId is invalid");
            return;
        }
        if (favorite.getPetId() == null || favorite.getPetId().isBlank()) {
            log.error("Favorite petId is invalid");
            return;
        }
        // Add or update favorite record - already cached
        log.info("User {} favorited pet {}", favorite.getUserId(), favorite.getPetId());
    }
}