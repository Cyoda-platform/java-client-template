package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counter for Job entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.Job> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    // Cache and ID counter for Pet entity
    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/jobs - create new Job entity
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Map<String, String> request) {
        String sourceUrl = request.get("sourceUrl");
        if (sourceUrl == null || sourceUrl.isBlank()) {
            log.error("Job creation failed: sourceUrl is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required and cannot be blank");
        }

        String newId = "job" + jobIdCounter.getAndIncrement();
        com.java_template.application.entity.Job job = new com.java_template.application.entity.Job();
        job.setId(newId);
        job.setSourceUrl(sourceUrl);
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus(com.java_template.application.entity.Job.JobStatusEnum.PENDING);

        if (!job.isValid()) {
            log.error("Job creation failed: validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid job data");
        }

        jobCache.put(newId, job);

        log.info("Created Job with ID: {}", newId);
        processJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /prototype/jobs/{id} - retrieve Job by id
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJob(@PathVariable String id) {
        com.java_template.application.entity.Job job = jobCache.get(id);
        if (job == null) {
            log.error("Job not found: ID {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/pets - create new Pet entity
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Map<String, Object> request) {
        // Validate required fields
        Object nameObj = request.get("name");
        Object categoryObj = request.get("category");
        Object statusObj = request.get("status");
        if (!(nameObj instanceof String) || ((String)nameObj).isBlank()) {
            log.error("Pet creation failed: name is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name is required and cannot be blank");
        }
        if (!(categoryObj instanceof String) || ((String)categoryObj).isBlank()) {
            log.error("Pet creation failed: category is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("category is required and cannot be blank");
        }
        if (!(statusObj instanceof String) || ((String)statusObj).isBlank()) {
            log.error("Pet creation failed: status is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("status is required and cannot be blank");
        }

        String newId = "pet" + petIdCounter.getAndIncrement();
        com.java_template.application.entity.Pet pet = new com.java_template.application.entity.Pet();
        pet.setId(newId);
        pet.setName((String)nameObj);
        pet.setCategory((String)categoryObj);

        // photoUrls - optional list of strings
        Object photoUrlsObj = request.get("photoUrls");
        if (photoUrlsObj instanceof List<?>) {
            List<String> photoUrls = new ArrayList<>();
            for (Object o : (List<?>)photoUrlsObj) {
                if (o instanceof String) {
                    photoUrls.add((String)o);
                }
            }
            pet.setPhotoUrls(photoUrls);
        } else {
            pet.setPhotoUrls(Collections.emptyList());
        }

        // tags - optional list of strings
        Object tagsObj = request.get("tags");
        if (tagsObj instanceof List<?>) {
            List<String> tags = new ArrayList<>();
            for (Object o : (List<?>)tagsObj) {
                if (o instanceof String) {
                    tags.add((String)o);
                }
            }
            pet.setTags(tags);
        } else {
            pet.setTags(Collections.emptyList());
        }

        try {
            pet.setStatus(com.java_template.application.entity.Pet.PetStatusEnum.valueOf(((String)statusObj).toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.error("Pet creation failed: invalid status value '{}'", statusObj);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid status value");
        }

        pet.setCreatedAt(LocalDateTime.now());

        if (!pet.isValid()) {
            log.error("Pet creation failed: validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data");
        }

        petCache.put(newId, pet);

        log.info("Created Pet with ID: {}", newId);
        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /prototype/pets/{id} - retrieve Pet by id
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        com.java_template.application.entity.Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found: ID {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // Process method for Job entity
    private void processJob(com.java_template.application.entity.Job job) {
        log.info("Processing Job with ID: {}", job.getId());

        // Validate sourceUrl format (basic)
        String sourceUrl = job.getSourceUrl();
        if (!sourceUrl.startsWith("http")) {
            log.error("Job processing failed: invalid sourceUrl '{}'", sourceUrl);
            job.setStatus(com.java_template.application.entity.Job.JobStatusEnum.FAILED);
            return;
        }

        job.setStatus(com.java_template.application.entity.Job.JobStatusEnum.PROCESSING);
        // Simulate fetching data from sourceUrl and creating Pet entities
        // For prototype, just log and simulate success

        // In real scenario, fetch pet data JSON from sourceUrl, parse, and create Pet entities here
        log.info("Fetching pet data from {}", sourceUrl);

        // Simulate creating a new Pet entity from fetched data (example)
        String petId = "pet" + petIdCounter.getAndIncrement();
        com.java_template.application.entity.Pet pet = new com.java_template.application.entity.Pet();
        pet.setId(petId);
        pet.setName("ImportedPet");
        pet.setCategory("cat");
        pet.setPhotoUrls(Collections.emptyList());
        pet.setTags(Collections.emptyList());
        pet.setStatus(com.java_template.application.entity.Pet.PetStatusEnum.AVAILABLE);
        pet.setCreatedAt(LocalDateTime.now());

        petCache.put(petId, pet);
        processPet(pet);

        job.setStatus(com.java_template.application.entity.Job.JobStatusEnum.COMPLETED);
        log.info("Completed processing Job with ID: {}", job.getId());
    }

    // Process method for Pet entity
    private void processPet(com.java_template.application.entity.Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        // Validate required fields
        if (pet.getName().isBlank() || pet.getCategory().isBlank()) {
            log.error("Pet processing failed: name or category is blank");
            return;
        }

        // Enrichment example: add default tag if tags list is empty
        if (pet.getTags() == null || pet.getTags().isEmpty()) {
            pet.setTags(Collections.singletonList("default"));
            log.info("Added default tag to Pet ID: {}", pet.getId());
        }

        // Enrichment example: add default photo URL if none provided
        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isEmpty()) {
            pet.setPhotoUrls(Collections.singletonList("http://example.com/default-pet.jpg"));
            log.info("Added default photo URL to Pet ID: {}", pet.getId());
        }

        log.info("Completed processing Pet with ID: {}", pet.getId());
    }
}