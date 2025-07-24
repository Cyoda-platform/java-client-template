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

    // Cache and ID counter for PurrfectPetsJob
    private final ConcurrentHashMap<String, PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    // Cache and ID counter for Pet
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/purrfectPetsJob - create new job and trigger processing
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        try {
            if (job == null) {
                return ResponseEntity.badRequest().body("Job cannot be null");
            }
            // Assign business ID if missing
            if (job.getId() == null || job.getId().isBlank()) {
                job.setId("job-" + purrfectPetsJobIdCounter.getAndIncrement());
            }
            // Set createdAt if missing
            if (job.getCreatedAt() == null) {
                job.setCreatedAt(java.time.LocalDateTime.now());
            }
            job.setStatus(PurrfectPetsJob.StatusEnum.PENDING);

            if (!job.isValid()) {
                return ResponseEntity.badRequest().body("Invalid job data");
            }

            purrfectPetsJobCache.put(job.getId(), job);
            log.info("Created PurrfectPetsJob with ID: {}", job.getId());

            processPurrfectPetsJob(job);

            return ResponseEntity.status(HttpStatus.CREATED).body(job);
        } catch (Exception e) {
            log.error("Error creating PurrfectPetsJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/purrfectPetsJob/{id} - get job by ID
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet - create new pet and trigger processing
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            if (pet == null) {
                return ResponseEntity.badRequest().body("Pet cannot be null");
            }
            if (pet.getId() == null || pet.getId().isBlank()) {
                pet.setId("pet-" + petIdCounter.getAndIncrement());
            }
            if (pet.getCreatedAt() == null) {
                pet.setCreatedAt(java.time.LocalDateTime.now());
            }
            if (pet.getLifecycleStatus() == null) {
                pet.setLifecycleStatus(Pet.StatusEnum.NEW);
            }
            if (!pet.isValid()) {
                return ResponseEntity.badRequest().body("Invalid pet data");
            }
            petCache.put(pet.getId(), pet);
            log.info("Created Pet with ID: {}", pet.getId());

            processPet(pet);

            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/pet/{id} - get pet by ID
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/purrfectPetsJob/{id}/update - create new job version (optional, avoid if possible)
    @PostMapping("/purrfectPetsJob/{id}/update")
    public ResponseEntity<?> updatePurrfectPetsJob(@PathVariable String id, @RequestBody PurrfectPetsJob updatedJob) {
        try {
            PurrfectPetsJob existingJob = purrfectPetsJobCache.get(id);
            if (existingJob == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            // Create new version entity
            updatedJob.setId("job-" + purrfectPetsJobIdCounter.getAndIncrement());
            if (updatedJob.getCreatedAt() == null) {
                updatedJob.setCreatedAt(java.time.LocalDateTime.now());
            }
            if (!updatedJob.isValid()) {
                return ResponseEntity.badRequest().body("Invalid updated job data");
            }
            updatedJob.setStatus(PurrfectPetsJob.StatusEnum.PENDING);
            purrfectPetsJobCache.put(updatedJob.getId(), updatedJob);
            log.info("Created new version of PurrfectPetsJob with ID: {}", updatedJob.getId());

            processPurrfectPetsJob(updatedJob);

            return ResponseEntity.status(HttpStatus.CREATED).body(updatedJob);
        } catch (Exception e) {
            log.error("Error updating PurrfectPetsJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // POST /prototype/pet/{id}/update - create new pet version (optional)
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet updatedPet) {
        try {
            Pet existingPet = petCache.get(id);
            if (existingPet == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            updatedPet.setId("pet-" + petIdCounter.getAndIncrement());
            if (updatedPet.getCreatedAt() == null) {
                updatedPet.setCreatedAt(java.time.LocalDateTime.now());
            }
            if (!updatedPet.isValid()) {
                return ResponseEntity.badRequest().body("Invalid updated pet data");
            }
            if (updatedPet.getLifecycleStatus() == null) {
                updatedPet.setLifecycleStatus(Pet.StatusEnum.NEW);
            }
            petCache.put(updatedPet.getId(), updatedPet);
            log.info("Created new version of Pet with ID: {}", updatedPet.getId());

            processPet(updatedPet);

            return ResponseEntity.status(HttpStatus.CREATED).body(updatedPet);
        } catch (Exception e) {
            log.error("Error updating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // POST /prototype/purrfectPetsJob/{id}/deactivate - create deactivation event (optional)
    @PostMapping("/purrfectPetsJob/{id}/deactivate")
    public ResponseEntity<?> deactivatePurrfectPetsJob(@PathVariable String id) {
        PurrfectPetsJob existingJob = purrfectPetsJobCache.get(id);
        if (existingJob == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        PurrfectPetsJob deactivatedJob = new PurrfectPetsJob();
        deactivatedJob.setId("job-" + purrfectPetsJobIdCounter.getAndIncrement());
        deactivatedJob.setJobId(existingJob.getJobId());
        deactivatedJob.setPetType(existingJob.getPetType());
        deactivatedJob.setAction(existingJob.getAction());
        deactivatedJob.setStatus(PurrfectPetsJob.StatusEnum.FAILED); // or a specific DEACTIVATED status if defined
        deactivatedJob.setCreatedAt(java.time.LocalDateTime.now());
        purrfectPetsJobCache.put(deactivatedJob.getId(), deactivatedJob);
        log.info("Deactivated PurrfectPetsJob with new ID: {}", deactivatedJob.getId());
        return ResponseEntity.ok("Job deactivated");
    }

    // POST /prototype/pet/{id}/deactivate - create deactivation event (optional)
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet deactivatedPet = new Pet();
        deactivatedPet.setId("pet-" + petIdCounter.getAndIncrement());
        deactivatedPet.setPetId(existingPet.getPetId());
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setCategory(existingPet.getCategory());
        deactivatedPet.setStatus(existingPet.getStatus());
        deactivatedPet.setPhotoUrls(existingPet.getPhotoUrls());
        deactivatedPet.setLifecycleStatus(Pet.StatusEnum.ARCHIVED); // or specific DEACTIVATED if defined
        deactivatedPet.setCreatedAt(java.time.LocalDateTime.now());
        petCache.put(deactivatedPet.getId(), deactivatedPet);
        log.info("Deactivated Pet with new ID: {}", deactivatedPet.getId());
        return ResponseEntity.ok("Pet deactivated");
    }

    // Business Logic for processing PurrfectPetsJob entity
    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());

        job.setStatus(PurrfectPetsJob.StatusEnum.PROCESSING);

        // Validate petType and action
        if (job.getPetType() == null || job.getPetType().isBlank()) {
            log.error("Invalid petType in job {}", job.getId());
            job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
            return;
        }
        if (job.getAction() == null || job.getAction().isBlank()) {
            log.error("Invalid action in job {}", job.getId());
            job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
            return;
        }

        // Simulate external Petstore API call for action "fetch"
        if ("fetch".equalsIgnoreCase(job.getAction())) {
            // Simulated pet data fetched for the petType
            List<Pet> fetchedPets = new ArrayList<>();
            // For demonstration, create 2 sample pets
            Pet pet1 = new Pet();
            pet1.setId("pet-" + petIdCounter.getAndIncrement());
            pet1.setPetId(UUID.randomUUID().toString());
            pet1.setName("SamplePet1");
            pet1.setCategory(job.getPetType());
            pet1.setStatus("available");
            pet1.setPhotoUrls(Collections.emptyList());
            pet1.setLifecycleStatus(Pet.StatusEnum.NEW);
            pet1.setCreatedAt(java.time.LocalDateTime.now());

            Pet pet2 = new Pet();
            pet2.setId("pet-" + petIdCounter.getAndIncrement());
            pet2.setPetId(UUID.randomUUID().toString());
            pet2.setName("SamplePet2");
            pet2.setCategory(job.getPetType());
            pet2.setStatus("available");
            pet2.setPhotoUrls(Collections.emptyList());
            pet2.setLifecycleStatus(Pet.StatusEnum.NEW);
            pet2.setCreatedAt(java.time.LocalDateTime.now());

            fetchedPets.add(pet1);
            fetchedPets.add(pet2);

            for (Pet pet : fetchedPets) {
                petCache.put(pet.getId(), pet);
                processPet(pet);
            }

            job.setStatus(PurrfectPetsJob.StatusEnum.COMPLETED);
            log.info("Completed processing PurrfectPetsJob with ID: {}", job.getId());
        } else {
            log.error("Unsupported action '{}' in job {}", job.getAction(), job.getId());
            job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
        }
    }

    // Business Logic for processing Pet entity
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate pet data
        if (!pet.isValid()) {
            log.error("Invalid pet data for pet ID: {}", pet.getId());
            return;
        }

        // Enrich pet data: add default photo URL if no photos provided
        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isEmpty()) {
            pet.setPhotoUrls(Collections.singletonList("http://default.photo.url/image.jpg"));
            log.info("Added default photo URL for pet ID: {}", pet.getId());
        }

        // Update lifecycle status to PROCESSED
        pet.setLifecycleStatus(Pet.StatusEnum.PROCESSED);

        log.info("Completed processing Pet with ID: {}", pet.getId());
    }
}