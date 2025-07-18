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

    // POST /prototype/jobs - create new PurrfectPetsJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody PurrfectPetsJob job) {
        if (job == null) {
            log.error("Received null job");
            return ResponseEntity.badRequest().body("Job cannot be null");
        }
        // Generate unique IDs
        String generatedId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setId(generatedId);
        job.setTechnicalId(UUID.randomUUID());
        if (job.getJobId() == null || job.getJobId().isBlank()) {
            job.setJobId(generatedId);
        }
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (!job.isValid()) {
            log.error("Invalid job data: {}", job);
            return ResponseEntity.badRequest().body("Invalid job data");
        }

        purrfectPetsJobCache.put(generatedId, job);
        log.info("Created job with ID: {}", generatedId);

        processPurrfectPetsJob(job);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("status", job.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/jobs/{id} - get job by id
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJobById(@PathVariable String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            log.error("Job not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pets - add new pet or pet state
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Received null pet");
            return ResponseEntity.badRequest().body("Pet cannot be null");
        }
        String generatedId = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(generatedId);
        pet.setTechnicalId(UUID.randomUUID());
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            pet.setPetId(generatedId);
        }
        if (!pet.isValid()) {
            log.error("Invalid pet data: {}", pet);
            return ResponseEntity.badRequest().body("Invalid pet data");
        }

        petCache.put(generatedId, pet);
        log.info("Created pet with ID: {}", generatedId);

        processPet(pet);

        Map<String, String> response = new HashMap<>();
        response.put("petId", pet.getPetId());
        response.put("status", pet.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/pets/{id} - get pet by id
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPetById(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/pets/{id}/update - create new pet version (immutable)
    @PostMapping("/pets/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            log.error("Pet not found for update with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        if (petUpdate == null) {
            log.error("Received null pet update");
            return ResponseEntity.badRequest().body("Pet update data cannot be null");
        }
        // Create new pet version with new ID
        String newId = "pet-" + petIdCounter.getAndIncrement();
        petUpdate.setId(newId);
        petUpdate.setTechnicalId(UUID.randomUUID());
        if (petUpdate.getPetId() == null || petUpdate.getPetId().isBlank()) {
            petUpdate.setPetId(newId);
        }
        if (!petUpdate.isValid()) {
            log.error("Invalid pet update data: {}", petUpdate);
            return ResponseEntity.badRequest().body("Invalid pet update data");
        }

        petCache.put(newId, petUpdate);
        log.info("Created new pet version with ID: {}", newId);

        processPet(petUpdate);

        Map<String, String> response = new HashMap<>();
        response.put("petId", petUpdate.getPetId());
        response.put("status", petUpdate.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /prototype/pets/{id}/deactivate - create deactivation record
    @PostMapping("/pets/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            log.error("Pet not found for deactivation with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        // Create new pet state with status "DEACTIVATED"
        String newId = "pet-" + petIdCounter.getAndIncrement();
        Pet deactivatedPet = new Pet();
        deactivatedPet.setId(newId);
        deactivatedPet.setTechnicalId(UUID.randomUUID());
        deactivatedPet.setPetId(existingPet.getPetId());
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setSpecies(existingPet.getSpecies());
        deactivatedPet.setBreed(existingPet.getBreed());
        deactivatedPet.setAge(existingPet.getAge());
        deactivatedPet.setStatus("DEACTIVATED");

        petCache.put(newId, deactivatedPet);
        log.info("Deactivated pet with new ID: {}", newId);

        Map<String, String> response = new HashMap<>();
        response.put("petId", deactivatedPet.getPetId());
        response.put("status", deactivatedPet.getStatus());
        return ResponseEntity.ok(response);
    }

    // GET /prototype/pets - list all pets
    @GetMapping("/pets")
    public ResponseEntity<?> listAllPets() {
        List<Pet> pets = new ArrayList<>(petCache.values());
        return ResponseEntity.ok(pets);
    }

    // Business logic for processing PurrfectPetsJob
    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());
        try {
            job.setStatus("PROCESSING");
            // Validate job type
            String jobType = job.getType();
            if (jobType == null || jobType.isBlank()) {
                throw new IllegalArgumentException("Job type is required");
            }

            if ("ImportPets".equalsIgnoreCase(jobType)) {
                // Simulate importing pets from external Petstore API
                // Here we mock the creation of some pets
                Pet pet1 = new Pet();
                pet1.setId("pet-" + petIdCounter.getAndIncrement());
                pet1.setTechnicalId(UUID.randomUUID());
                pet1.setPetId(pet1.getId());
                pet1.setName("Whiskers");
                pet1.setSpecies("Cat");
                pet1.setBreed("Siamese");
                pet1.setAge(3);
                pet1.setStatus("AVAILABLE");
                petCache.put(pet1.getId(), pet1);
                processPet(pet1);

                Pet pet2 = new Pet();
                pet2.setId("pet-" + petIdCounter.getAndIncrement());
                pet2.setTechnicalId(UUID.randomUUID());
                pet2.setPetId(pet2.getId());
                pet2.setName("Fido");
                pet2.setSpecies("Dog");
                pet2.setBreed("Beagle");
                pet2.setAge(5);
                pet2.setStatus("AVAILABLE");
                petCache.put(pet2.getId(), pet2);
                processPet(pet2);

            } else if ("UpdatePetStatus".equalsIgnoreCase(jobType)) {
                // Example: change all AVAILABLE pets to PENDING (mock logic)
                for (Pet pet : petCache.values()) {
                    if ("AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
                        Pet newPetState = new Pet();
                        newPetState.setId("pet-" + petIdCounter.getAndIncrement());
                        newPetState.setTechnicalId(UUID.randomUUID());
                        newPetState.setPetId(pet.getPetId());
                        newPetState.setName(pet.getName());
                        newPetState.setSpecies(pet.getSpecies());
                        newPetState.setBreed(pet.getBreed());
                        newPetState.setAge(pet.getAge());
                        newPetState.setStatus("PENDING");
                        petCache.put(newPetState.getId(), newPetState);
                        processPet(newPetState);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unsupported job type: " + jobType);
            }

            job.setStatus("COMPLETED");
            log.info("Job {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Error processing job {}: {}", job.getId(), e.getMessage());
        }
    }

    // Business logic for processing Pet entity
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate pet data
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet name is required for pet ID: {}", pet.getId());
            return;
        }
        if (pet.getAge() != null && pet.getAge() < 0) {
            log.error("Invalid pet age for pet ID: {}", pet.getId());
            return;
        }
        // Additional business logic such as assigning adoption status or enrichment could be here
        log.info("Pet processed: {} ({})", pet.getName(), pet.getStatus());
    }
}