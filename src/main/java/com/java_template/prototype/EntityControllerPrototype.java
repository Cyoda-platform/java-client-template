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

    // ------------------ PetIngestionJob Endpoints ------------------

    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob jobRequest) {
        if (jobRequest == null || jobRequest.getSource() == null || jobRequest.getSource().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing source");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: source");
        }

        String newId = "job-" + petIngestionJobIdCounter.getAndIncrement();
        PetIngestionJob newJob = new PetIngestionJob();
        newJob.setId(newId);
        newJob.setJobId(newId);
        newJob.setSource(jobRequest.getSource());
        newJob.setStatus("PENDING");
        newJob.setCreatedAt(java.time.LocalDateTime.now());

        petIngestionJobCache.put(newId, newJob);
        log.info("Created PetIngestionJob with ID: {}", newId);

        processPetIngestionJob(newJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(newJob);
    }

    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        PetIngestionJob job = petIngestionJobCache.get(id);
        if (job == null) {
            log.error("PetIngestionJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // ------------------ Pet Endpoints ------------------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet petRequest) {
        if (petRequest == null || !validatePetRequest(petRequest)) {
            log.error("Invalid Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required pet fields");
        }

        String newId = "pet-" + petIdCounter.getAndIncrement();
        Pet newPet = new Pet();
        newPet.setId(newId);
        newPet.setPetId(newId);
        newPet.setName(petRequest.getName());
        newPet.setCategory(petRequest.getCategory());
        newPet.setPhotoUrls(petRequest.getPhotoUrls() != null ? petRequest.getPhotoUrls() : new ArrayList<>());
        newPet.setTags(petRequest.getTags() != null ? petRequest.getTags() : new ArrayList<>());
        newPet.setStatus("NEW");

        petCache.put(newId, newPet);
        log.info("Created Pet with ID: {}", newId);

        processPet(newPet);

        return ResponseEntity.status(HttpStatus.CREATED).body(newPet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // ------------------ Process Methods ------------------

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with ID: {}", job.getId());

        // 1. Validation already done in create method

        // 2. Update status to PROCESSING
        job.setStatus("PROCESSING");
        petIngestionJobCache.put(job.getId(), job);
        log.info("PetIngestionJob {} status updated to PROCESSING", job.getId());

        // 3. Fetch data from Petstore API (simulated here)
        // For prototype, simulate fetching 2 pets
        List<Pet> fetchedPets = new ArrayList<>();

        Pet pet1 = new Pet();
        pet1.setId("pet-" + petIdCounter.getAndIncrement());
        pet1.setPetId(pet1.getId());
        pet1.setName("Whiskers");
        pet1.setCategory("cat");
        pet1.setPhotoUrls(Arrays.asList("http://image1.jpg", "http://image2.jpg"));
        pet1.setTags(Arrays.asList("cute", "playful"));
        pet1.setStatus("NEW");

        Pet pet2 = new Pet();
        pet2.setId("pet-" + petIdCounter.getAndIncrement());
        pet2.setPetId(pet2.getId());
        pet2.setName("Barkley");
        pet2.setCategory("dog");
        pet2.setPhotoUrls(Arrays.asList("http://dogimage1.jpg"));
        pet2.setTags(Arrays.asList("friendly", "energetic"));
        pet2.setStatus("NEW");

        fetchedPets.add(pet1);
        fetchedPets.add(pet2);

        // 4. Persist pets and process each
        for (Pet pet : fetchedPets) {
            petCache.put(pet.getId(), pet);
            log.info("Persisted Pet from ingestion: {}", pet.getId());
            processPet(pet);
        }

        // 5. Update job status to COMPLETED
        job.setStatus("COMPLETED");
        petIngestionJobCache.put(job.getId(), job);
        log.info("PetIngestionJob {} status updated to COMPLETED", job.getId());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validation already done on creation

        // Enrichment: add fun fact tag if none present
        if (pet.getTags() == null || pet.getTags().isEmpty()) {
            pet.setTags(new ArrayList<>(Arrays.asList("fun pet")));
            log.info("Added default fun pet tag to Pet {}", pet.getId());
        }

        // Mark pet as AVAILABLE if currently NEW
        if ("NEW".equalsIgnoreCase(pet.getStatus())) {
            pet.setStatus("AVAILABLE");
            petCache.put(pet.getId(), pet);
            log.info("Pet {} status updated to AVAILABLE", pet.getId());
        }

        // Finalize pet entity state (no further action for now)
    }

    // ------------------ Helper Methods ------------------

    private boolean validatePetRequest(Pet pet) {
        return pet != null
                && pet.getName() != null && !pet.getName().isBlank()
                && pet.getCategory() != null && !pet.getCategory().isBlank();
    }
}