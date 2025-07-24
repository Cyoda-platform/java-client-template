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

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for PetIngestionJob entity
    private final ConcurrentHashMap<String, PetIngestionJob> petIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petIngestionJobIdCounter = new AtomicLong(1);

    // Caches and ID counters for Pet entity
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // ------ PetIngestionJob endpoints ------

    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) {
        // Validate required fields
        if (job.getSource() == null || job.getSource().isBlank()) {
            log.error("PetIngestionJob creation failed: source is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Source URL is required");
        }

        // Generate IDs
        String id = String.valueOf(petIngestionJobIdCounter.getAndIncrement());
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());

        petIngestionJobCache.put(id, job);

        log.info("Created PetIngestionJob with id: {}", id);

        // Trigger processing event
        processPetIngestionJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        PetIngestionJob job = petIngestionJobCache.get(id);
        if (job == null) {
            log.error("PetIngestionJob with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // ------ Pet endpoints ------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        // Validate required fields
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: name is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet creation failed: category is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet creation failed: status is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet status is required");
        }

        // Generate IDs
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());

        petCache.put(id, pet);

        log.info("Created Pet with id: {}", id);

        // Trigger processing event
        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/pets")
    public ResponseEntity<?> getPets(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String status) {
        List<Pet> filteredPets = new ArrayList<>();
        for (Pet pet : petCache.values()) {
            boolean matchesCategory = (category == null) || category.equalsIgnoreCase(pet.getCategory());
            boolean matchesStatus = (status == null) || status.equalsIgnoreCase(pet.getStatus());
            if (matchesCategory && matchesStatus) {
                filteredPets.add(pet);
            }
        }
        return ResponseEntity.ok(filteredPets);
    }

    // ------ Event-driven processing methods ------

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with ID: {}", job.getId());
        try {
            job.setStatus("PROCESSING");

            // Validate source URL (basic check)
            if (job.getSource() == null || job.getSource().isBlank()) {
                log.error("Invalid source URL for PetIngestionJob {}", job.getId());
                job.setStatus("FAILED");
                return;
            }

            // Simulate fetch data from Petstore API (mocked here)
            // In real implementation, call external API, parse response etc.
            log.info("Fetching pet data from source: {}", job.getSource());
            // Example: simulate creation of some Pet entities
            Pet pet1 = new Pet();
            pet1.setId(String.valueOf(petIdCounter.getAndIncrement()));
            pet1.setTechnicalId(UUID.randomUUID());
            pet1.setPetId("pet-001");
            pet1.setName("Fluffy");
            pet1.setCategory("Cat");
            pet1.setPhotoUrls(Collections.singletonList("http://example.com/fluffy.jpg"));
            pet1.setTags(Arrays.asList("cute", "small"));
            pet1.setStatus("AVAILABLE");
            petCache.put(pet1.getId(), pet1);
            processPet(pet1);

            Pet pet2 = new Pet();
            pet2.setId(String.valueOf(petIdCounter.getAndIncrement()));
            pet2.setTechnicalId(UUID.randomUUID());
            pet2.setPetId("pet-002");
            pet2.setName("Buddy");
            pet2.setCategory("Dog");
            pet2.setPhotoUrls(Collections.singletonList("http://example.com/buddy.jpg"));
            pet2.setTags(Arrays.asList("friendly", "large"));
            pet2.setStatus("AVAILABLE");
            petCache.put(pet2.getId(), pet2);
            processPet(pet2);

            job.setStatus("COMPLETED");
            log.info("PetIngestionJob {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Error processing PetIngestionJob {}: {}", job.getId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Business logic:
        // - Validate pet fields
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet name is blank for Pet ID: {}", pet.getId());
            return;
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet category is blank for Pet ID: {}", pet.getId());
            return;
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet status is blank for Pet ID: {}", pet.getId());
            return;
        }

        // - Enrich data if needed (e.g., set defaults)
        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new ArrayList<>());
        }

        // - Could trigger notifications or downstream workflows here
        log.info("Pet {} processed successfully", pet.getId());
    }
}