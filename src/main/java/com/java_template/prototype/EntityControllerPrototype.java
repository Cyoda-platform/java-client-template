package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.application.entity.Pet;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetRegistrationJob> petRegistrationJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petRegistrationJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/petRegistrationJob
    @PostMapping("/petRegistrationJob")
    public ResponseEntity<?> createPetRegistrationJob(@RequestBody PetRegistrationJob job) {
        if (job.getSource() == null || job.getSource().isBlank()) {
            log.error("PetRegistrationJob creation failed: source is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: source");
        }
        String id = "job-" + petRegistrationJobIdCounter.getAndIncrement();
        job.setId(id);
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());
        petRegistrationJobCache.put(id, job);
        log.info("Created PetRegistrationJob with ID: {}", id);
        processPetRegistrationJob(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /prototype/petRegistrationJob/{id}
    @GetMapping("/petRegistrationJob/{id}")
    public ResponseEntity<?> getPetRegistrationJob(@PathVariable String id) {
        PetRegistrationJob job = petRegistrationJobCache.get(id);
        if (job == null) {
            log.error("PetRegistrationJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetRegistrationJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: name is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: name");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet creation failed: category is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: category");
        }
        String id = "pet-" + petIdCounter.getAndIncrement();
        pet.setPetId(id);
        pet.setStatus(pet.getStatus() != null && !pet.getStatus().isBlank() ? pet.getStatus() : "available");
        petCache.put(id, pet);
        log.info("Created Pet with ID: {}", id);
        processPet(pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{petId}
    @GetMapping("/pet/{petId}")
    public ResponseEntity<?> getPet(@PathVariable String petId) {
        Pet pet = petCache.get(petId);
        if (pet == null) {
            log.error("Pet not found: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // GET /prototype/pet
    @GetMapping("/pet")
    public ResponseEntity<?> listPets(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String category) {
        List<Pet> filteredPets = new ArrayList<>();
        for (Pet pet : petCache.values()) {
            boolean matches = true;
            if (status != null && !status.isBlank()) {
                matches = pet.getStatus() != null && pet.getStatus().equalsIgnoreCase(status);
            }
            if (matches && category != null && !category.isBlank()) {
                matches = pet.getCategory() != null && pet.getCategory().equalsIgnoreCase(category);
            }
            if (matches) {
                filteredPets.add(pet);
            }
        }
        return ResponseEntity.ok(filteredPets);
    }

    private void processPetRegistrationJob(PetRegistrationJob job) {
        log.info("Processing PetRegistrationJob with ID: {}", job.getId());
        try {
            job.setStatus("PROCESSING");
            // Validate source connectivity (simulate)
            if (!"PetstoreAPI".equalsIgnoreCase(job.getSource())) {
                log.error("Unsupported source: {}", job.getSource());
                job.setStatus("FAILED");
                return;
            }
            // Simulate fetching pets from Petstore API
            List<Pet> fetchedPets = new ArrayList<>();
            Pet samplePet = new Pet();
            samplePet.setPetId("pet-external-1");
            samplePet.setName("Whiskers");
            samplePet.setCategory("cat");
            samplePet.setPhotoUrls(List.of("http://example.com/whiskers.jpg"));
            samplePet.setTags(List.of("fluffy", "gray"));
            samplePet.setStatus("available");
            fetchedPets.add(samplePet);

            // Save new Pet entities immutably
            for (Pet pet : fetchedPets) {
                if (!petCache.containsKey(pet.getPetId())) {
                    petCache.put(pet.getPetId(), pet);
                    processPet(pet);
                    log.info("Imported Pet with ID: {}", pet.getPetId());
                } else {
                    log.info("Pet with ID {} already exists. Skipping import.", pet.getPetId());
                }
            }
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Error processing PetRegistrationJob with ID {}: {}", job.getId(), e.getMessage());
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getPetId());
        // Validate mandatory fields
        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet validation failed for ID {}: Missing name or category", pet.getPetId());
            return;
        }
        // Optional enrichment: normalize tags to lowercase
        if (pet.getTags() != null) {
            List<String> normalizedTags = new ArrayList<>();
            for (String tag : pet.getTags()) {
                if (tag != null) {
                    normalizedTags.add(tag.toLowerCase());
                }
            }
            pet.setTags(normalizedTags);
        }
        // Validate photo URLs (basic check)
        if (pet.getPhotoUrls() != null) {
            List<String> validUrls = new ArrayList<>();
            for (String url : pet.getPhotoUrls()) {
                if (url != null && url.startsWith("http")) {
                    validUrls.add(url);
                } else {
                    log.warn("Invalid photo URL skipped for Pet ID {}: {}", pet.getPetId(), url);
                }
            }
            pet.setPhotoUrls(validUrls);
        }
        // Persistence finalized by cache save done in create methods
        log.info("Pet with ID {} processed successfully", pet.getPetId());
    }
}