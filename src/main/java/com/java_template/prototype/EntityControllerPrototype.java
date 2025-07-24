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

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/jobs - create a new PurrfectPetsJob and trigger processing
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody PurrfectPetsJob job) {
        if (job == null) {
            log.error("Received null job");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Job payload must not be null"));
        }
        if (!job.isValid()) {
            log.error("Invalid job received: {}", job);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid job data"));
        }

        String id = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setStatus("PENDING");
        purrfectPetsJobCache.put(id, job);
        log.info("Created job with ID: {}", id);

        try {
            processPurrfectPetsJob(id, job);
        } catch (Exception e) {
            log.error("Error processing job ID {}: {}", id, e.getMessage());
            job.setStatus("FAILED");
            purrfectPetsJobCache.put(id, job);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to process job"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    // GET /prototype/jobs/{id} - retrieve job by technicalId
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJob(@PathVariable String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            log.error("Job not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/pets/{id} - retrieve pet by technicalId
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        }
        return ResponseEntity.ok(pet);
    }

    // processPurrfectPetsJob - main business logic for job processing
    private void processPurrfectPetsJob(String jobId, PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", jobId);
        job.setStatus("PROCESSING");
        purrfectPetsJobCache.put(jobId, job);

        try {
            // Validate requestedAction
            String action = job.getRequestedAction().toUpperCase(Locale.ROOT);
            if ("LOAD_PETS".equals(action)) {
                // Simulate fetching pets from Petstore API filtered by status "available"
                // For prototype, create dummy pets and save immutably
                List<Pet> fetchedPets = fetchPetsFromPetstoreAPI("available");
                for (Pet pet : fetchedPets) {
                    savePetImmutable(pet);
                }
            } else if ("SAVE_PET".equals(action)) {
                // For SAVE_PET, normally would save a pet entity passed externally
                // Here we simulate by logging (no pet passed in job)
                log.info("SAVE_PET action requested but no pet data in job - skipping actual save");
            } else {
                log.error("Unknown requestedAction: {}", action);
                job.setStatus("FAILED");
                purrfectPetsJobCache.put(jobId, job);
                return;
            }

            job.setStatus("COMPLETED");
            purrfectPetsJobCache.put(jobId, job);
            log.info("Completed processing PurrfectPetsJob with ID: {}", jobId);
        } catch (Exception ex) {
            log.error("Exception processing PurrfectPetsJob ID {}: {}", jobId, ex.getMessage());
            job.setStatus("FAILED");
            purrfectPetsJobCache.put(jobId, job);
        }
    }

    // processPet - business logic for pet processing
    private void processPet(String petId, Pet pet) {
        log.info("Processing Pet with ID: {}", petId);
        // Validate pet fields
        if (!pet.isValid()) {
            log.error("Invalid pet data for ID: {}", petId);
            return;
        }
        // Enrich pet by adding default tag "Purrfect" if not present
        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        if (!pet.getTags().contains("Purrfect")) {
            pet.getTags().add("Purrfect");
        }
        // Pet status remains immutable in this prototype
        log.info("Processed Pet with ID: {}", petId);
    }

    // Save pet immutably: generate id, save in cache, and process
    private void savePetImmutable(Pet pet) {
        String id = "pet-" + petIdCounter.getAndIncrement();
        petCache.put(id, pet);
        log.info("Saved Pet immutably with ID: {}", id);
        processPet(id, pet);
    }

    // Simulated external API call to Petstore API to fetch pets by status
    private List<Pet> fetchPetsFromPetstoreAPI(String status) {
        // Prototype: create dummy pets to simulate external API call response
        List<Pet> pets = new ArrayList<>();

        Pet pet1 = new Pet();
        pet1.setPetId(101L);
        pet1.setName("Fluffy");
        pet1.setCategory("Cat");
        pet1.setStatus(status);
        pet1.setPhotoUrls(List.of("http://example.com/photo1.jpg"));
        pet1.setTags(new ArrayList<>(List.of("Cute")));

        Pet pet2 = new Pet();
        pet2.setPetId(102L);
        pet2.setName("Barky");
        pet2.setCategory("Dog");
        pet2.setStatus(status);
        pet2.setPhotoUrls(List.of("http://example.com/photo2.jpg"));
        pet2.setTags(new ArrayList<>(List.of("Friendly")));

        pets.add(pet1);
        pets.add(pet2);

        log.info("Fetched {} pets from Petstore API with status '{}'", pets.size(), status);
        return pets;
    }
}