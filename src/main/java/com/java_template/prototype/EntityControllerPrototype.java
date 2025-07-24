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
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/purrfectPetsJob - create a new job
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<Map<String, String>> createPurrfectPetsJob(@RequestBody Map<String, String> request) {
        String petStatus = request.get("petStatus");
        if (petStatus == null || petStatus.isBlank()) {
            log.error("Invalid petStatus in request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "petStatus is required and cannot be blank"));
        }

        PurrfectPetsJob job = new PurrfectPetsJob();
        String technicalId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
        job.setTechnicalId(technicalId);
        job.setPetStatus(petStatus);
        job.setRequestedAt(java.time.Instant.now().toString());
        job.setStatus("PENDING");
        job.setResultSummary("");

        purrfectPetsJobCache.put(technicalId, job);

        processPurrfectPetsJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/purrfectPetsJob/{id} - retrieve job by technicalId
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable("id") String id) {
        PurrfectPetsJob job = purrfectPetsJobCache.get(id);
        if (job == null) {
            log.error("PurrfectPetsJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PurrfectPetsJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/pet/{id} - retrieve pet by technicalId
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable("id") String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        }
        return ResponseEntity.ok(pet);
    }

    // Processing method for PurrfectPetsJob - ingest pets from Petstore API
    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with ID: {}", job.getTechnicalId());
        job.setStatus("PROCESSING");
        try {
            // Validate petStatus
            if (job.getPetStatus() == null || job.getPetStatus().isBlank()) {
                log.error("Job validation failed: petStatus is blank");
                job.setStatus("FAILED");
                job.setResultSummary("Validation failed: petStatus is blank");
                return;
            }

            // Call Petstore API GET /pet/findByStatus?status={petStatus}
            String petstoreUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + job.getPetStatus();
            Pet[] petsFromApi = restTemplate.getForObject(petstoreUrl, Pet[].class);

            if (petsFromApi == null) {
                log.error("No pets returned from Petstore API");
                job.setStatus("FAILED");
                job.setResultSummary("No pets returned from Petstore API");
                return;
            }

            int ingestedCount = 0;
            for (Pet apiPet : petsFromApi) {
                if (apiPet == null) continue;

                Pet pet = new Pet();
                String petTechnicalId = "pet-" + petIdCounter.getAndIncrement();
                pet.setPetId(apiPet.getPetId());
                pet.setName(apiPet.getName());
                pet.setCategory(apiPet.getCategory());
                pet.setPhotoUrls(apiPet.getPhotoUrls());
                pet.setTags(apiPet.getTags());
                pet.setStatus(apiPet.getStatus());
                pet.setIngestedAt(java.time.Instant.now().toString());

                if (!pet.isValid()) {
                    log.error("Invalid pet data skipped: {}", pet);
                    continue;
                }

                petCache.put(petTechnicalId, pet);
                ingestedCount++;

                processPet(pet);
            }

            job.setStatus("COMPLETED");
            job.setResultSummary("Ingested " + ingestedCount + " pets");
            log.info("Job {} completed, ingested {} pets", job.getTechnicalId(), ingestedCount);

        } catch (Exception ex) {
            log.error("Error processing PurrfectPetsJob {}: {}", job.getTechnicalId(), ex.getMessage());
            job.setStatus("FAILED");
            job.setResultSummary("Error during processing: " + ex.getMessage());
        }
    }

    // Processing method for Pet entity
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getPetId());
        // Validation already done during creation
        // Enrichment or additional business logic could go here
        // For prototype, just log successful processing
        log.info("Pet processed successfully: {}", pet.getName());
    }
}