package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID generation for PurrfectPetsJob
    private final ConcurrentHashMap<String, com.java_template.application.entity.PurrfectPetsJob> purrfectPetsJobCache = new ConcurrentHashMap<>();
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);

    // Cache and ID generation for Pet
    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and ID generation for AdoptionRequest
    private final ConcurrentHashMap<String, com.java_template.application.entity.AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // POST /prototype/purrfectPetsJob - create new job
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody com.java_template.application.entity.PurrfectPetsJob job) {
        if (job == null || !job.isValid()) {
            log.error("Invalid PurrfectPetsJob provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PurrfectPetsJob data");
        }
        String id = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        job.setStatus("PENDING");
        purrfectPetsJobCache.put(id, job);
        log.info("Created PurrfectPetsJob with ID: {}", id);

        processPurrfectPetsJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /prototype/purrfectPetsJob/{id} - get job by id
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) {
        com.java_template.application.entity.PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            log.error("PurrfectPetsJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PurrfectPetsJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pet - create new pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody com.java_template.application.entity.Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }
        String id = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());
        pet.setStatus("AVAILABLE");
        petCache.put(id, pet);
        log.info("Created Pet with ID: {}", id);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pet/{id} - get pet by id
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        com.java_template.application.entity.Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/adoptionRequest - create new adoption request
    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody com.java_template.application.entity.AdoptionRequest request) {
        if (request == null || !request.isValid()) {
            log.error("Invalid AdoptionRequest provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid AdoptionRequest data");
        }
        String id = "req-" + adoptionRequestIdCounter.getAndIncrement();
        request.setId(id);
        request.setTechnicalId(UUID.randomUUID());
        request.setStatus("PENDING");
        if (request.getRequestDate() == null) {
            request.setRequestDate(java.time.LocalDateTime.now());
        }
        adoptionRequestCache.put(id, request);
        log.info("Created AdoptionRequest with ID: {}", id);

        processAdoptionRequest(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    // GET /prototype/adoptionRequest/{id} - get adoption request by id
    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        com.java_template.application.entity.AdoptionRequest request = adoptionRequestCache.get(id);
        if (request == null) {
            log.error("AdoptionRequest not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(request);
    }

    private void processPurrfectPetsJob(com.java_template.application.entity.PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getId());
        try {
            // Validate action and payload
            String action = job.getAction();
            String payload = job.getPayload();
            if (action == null || action.isBlank() || payload == null || payload.isBlank()) {
                job.setStatus("FAILED");
                log.error("Invalid action or payload in job {}", job.getId());
                return;
            }

            // Process based on action
            if (action.equalsIgnoreCase("AddPet")) {
                // Parse payload to Pet object
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.java_template.application.entity.Pet pet = mapper.readValue(payload, com.java_template.application.entity.Pet.class);
                // Create new Pet entity immutably
                pet.setId("pet-" + petIdCounter.getAndIncrement());
                pet.setTechnicalId(UUID.randomUUID());
                pet.setStatus("AVAILABLE");
                petCache.put(pet.getId(), pet);
                processPet(pet);
            } else if (action.equalsIgnoreCase("AdoptPet")) {
                // Parse payload to AdoptionRequest
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.java_template.application.entity.AdoptionRequest adoptionRequest = mapper.readValue(payload, com.java_template.application.entity.AdoptionRequest.class);
                adoptionRequest.setId("req-" + adoptionRequestIdCounter.getAndIncrement());
                adoptionRequest.setTechnicalId(UUID.randomUUID());
                adoptionRequest.setStatus("PENDING");
                if (adoptionRequest.getRequestDate() == null) {
                    adoptionRequest.setRequestDate(java.time.LocalDateTime.now());
                }
                adoptionRequestCache.put(adoptionRequest.getId(), adoptionRequest);
                processAdoptionRequest(adoptionRequest);
            } else {
                log.warn("Unknown job action: {}", action);
                job.setStatus("FAILED");
                return;
            }
            job.setStatus("COMPLETED");
            log.info("Job {} processed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Error processing job {}: {}", job.getId(), e.getMessage());
        }
    }

    private void processPet(com.java_template.application.entity.Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate mandatory fields already done, enrich or verify breed
        List<String> allowedBreeds = Arrays.asList("Siamese", "Persian", "Maine Coon", "Bulldog", "Beagle");
        if (!allowedBreeds.contains(pet.getBreed())) {
            log.warn("Breed {} is not in allowed list for pet {}", pet.getBreed(), pet.getId());
            // Optionally, set status to PENDING if breed unverified
            pet.setStatus("PENDING");
        } else {
            pet.setStatus("AVAILABLE");
        }
    }

    private void processAdoptionRequest(com.java_template.application.entity.AdoptionRequest request) {
        log.info("Processing AdoptionRequest with ID: {}", request.getId());
        com.java_template.application.entity.Pet pet = petCache.values().stream()
                .filter(p -> p.getPetId().equals(request.getPetId()))
                .findFirst()
                .orElse(null);
        if (pet == null) {
            log.error("Pet with ID {} not found for adoption request {}", request.getPetId(), request.getId());
            request.setStatus("REJECTED");
            return;
        }
        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            log.warn("Pet {} is not available for adoption", pet.getId());
            request.setStatus("REJECTED");
            return;
        }
        // Approve request and update pet status immutably
        request.setStatus("APPROVED");
        pet.setStatus("ADOPTED");
        log.info("AdoptionRequest {} approved and Pet {} marked as ADOPTED", request.getId(), pet.getId());
    }
}