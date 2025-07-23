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
import com.java_template.application.entity.AdoptionRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // PetJob cache and ID generator
    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    // Pet cache and ID generator
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // AdoptionRequest cache and ID generator
    private final ConcurrentHashMap<String, AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // POST /prototype/petJobs - Create PetJob
    @PostMapping("/petJobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        try {
            if (petJob == null || petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
            }
            String id = String.valueOf(petJobIdCounter.getAndIncrement());
            petJob.setId(id);
            petJob.setJobId(UUID.randomUUID().toString());
            petJob.setCreatedAt(java.time.LocalDateTime.now());
            petJob.setStatus("PENDING");
            petJob.setTechnicalId(UUID.randomUUID());

            petJobCache.put(id, petJob);
            processPetJob(petJob);

            return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
        } catch (Exception e) {
            log.error("Error creating PetJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/petJobs/{id} - Retrieve PetJob
    @GetMapping("/petJobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // POST /prototype/pets - Create Pet
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            if (pet == null || pet.getName() == null || pet.getName().isBlank()
                    || pet.getSpecies() == null || pet.getSpecies().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Required pet fields missing");
            }
            String id = String.valueOf(petIdCounter.getAndIncrement());
            pet.setId(id);
            pet.setPetId(UUID.randomUUID().toString());
            pet.setStatus("NEW");
            pet.setTechnicalId(UUID.randomUUID());

            petCache.put(id, pet);
            processPet(pet);

            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/pets/{id} - Retrieve Pet
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/adoptionRequests - Create AdoptionRequest
    @PostMapping("/adoptionRequests")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest request) {
        try {
            if (request == null || request.getPetId() == null || request.getPetId().isBlank()
                    || request.getRequesterName() == null || request.getRequesterName().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Required adoption request fields missing");
            }
            // Check pet availability
            boolean petAvailable = petCache.values().stream()
                    .anyMatch(p -> p.getPetId().equals(request.getPetId()) && ("NEW".equals(p.getStatus()) || "AVAILABLE".equals(p.getStatus())));
            if (!petAvailable) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet not available for adoption");
            }

            String id = String.valueOf(adoptionRequestIdCounter.getAndIncrement());
            request.setId(id);
            request.setRequestId(UUID.randomUUID().toString());
            request.setRequestDate(java.time.LocalDateTime.now());
            request.setStatus("PENDING");
            request.setTechnicalId(UUID.randomUUID());

            adoptionRequestCache.put(id, request);
            processAdoptionRequest(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(request);
        } catch (Exception e) {
            log.error("Error creating AdoptionRequest", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/adoptionRequests/{id} - Retrieve AdoptionRequest
    @GetMapping("/adoptionRequests/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        AdoptionRequest request = adoptionRequestCache.get(id);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(request);
    }

    // Business logic implementations

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());
        try {
            // Validate sourceUrl
            if (petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
                petJob.setStatus("FAILED");
                log.error("PetJob sourceUrl is invalid");
                return;
            }
            petJob.setStatus("PROCESSING");

            // Simulate fetching pet data from external Petstore API
            // For prototype, just create dummy pets
            Pet pet1 = new Pet();
            pet1.setId(String.valueOf(petIdCounter.getAndIncrement()));
            pet1.setPetId(UUID.randomUUID().toString());
            pet1.setName("Fluffy");
            pet1.setSpecies("Cat");
            pet1.setBreed("Persian");
            pet1.setAge(3);
            pet1.setStatus("NEW");
            pet1.setTechnicalId(UUID.randomUUID());
            petCache.put(pet1.getId(), pet1);
            processPet(pet1);

            Pet pet2 = new Pet();
            pet2.setId(String.valueOf(petIdCounter.getAndIncrement()));
            pet2.setPetId(UUID.randomUUID().toString());
            pet2.setName("Buddy");
            pet2.setSpecies("Dog");
            pet2.setBreed("Golden Retriever");
            pet2.setAge(5);
            pet2.setStatus("NEW");
            pet2.setTechnicalId(UUID.randomUUID());
            petCache.put(pet2.getId(), pet2);
            processPet(pet2);

            petJob.setStatus("COMPLETED");
            log.info("PetJob processing completed successfully for ID: {}", petJob.getId());
        } catch (Exception e) {
            petJob.setStatus("FAILED");
            log.error("Error processing PetJob with ID: {}", petJob.getId(), e);
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate required fields
        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getSpecies() == null || pet.getSpecies().isBlank()) {
            log.error("Pet validation failed for ID: {}", pet.getId());
            return;
        }
        // Additional business logic: e.g. notify systems, index for search, etc.
        log.info("Pet processed successfully with name: {}", pet.getName());
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        log.info("Processing AdoptionRequest with ID: {}", request.getId());
        // Validate pet availability again
        Optional<Pet> petOpt = petCache.values().stream()
                .filter(p -> p.getPetId().equals(request.getPetId()))
                .findFirst();
        if (petOpt.isEmpty() || (!"NEW".equals(petOpt.get().getStatus()) && !"AVAILABLE".equals(petOpt.get().getStatus()))) {
            request.setStatus("REJECTED");
            log.info("AdoptionRequest rejected due to pet unavailability for ID: {}", request.getId());
            return;
        }
        // Simulate approval (in real case, some workflow or manual approval)
        request.setStatus("APPROVED");
        log.info("AdoptionRequest approved for ID: {}", request.getId());
        // Optional: notify requester
    }
}