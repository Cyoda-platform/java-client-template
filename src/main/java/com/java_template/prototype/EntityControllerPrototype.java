package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Subscriber;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // --- JOB ENDPOINTS ---

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Job job) {
        if (!job.isValid()) {
            log.error("Invalid Job data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Job data"));
        }
        long id = jobIdCounter.getAndIncrement();
        String technicalId = "job-" + id;
        job.setCreatedAt(java.time.Instant.now().toString());
        job.setStatus("PENDING");
        jobCache.put(technicalId, job);
        log.info("Job created with technicalId={}", technicalId);
        processJob(technicalId, job);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // --- PET ENDPOINTS ---

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<Pet> getPet(@PathVariable String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            log.error("Pet not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPets(@RequestParam(required = false) String status) {
        List<Pet> pets = new ArrayList<>();
        for (Pet pet : petCache.values()) {
            if (status == null || status.isBlank() || status.equalsIgnoreCase(pet.getStatus())) {
                pets.add(pet);
            }
        }
        return ResponseEntity.ok(pets);
    }

    // --- SUBSCRIBER ENDPOINTS ---

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        if (!subscriber.isValid()) {
            log.error("Invalid Subscriber data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Subscriber data"));
        }
        long id = subscriberIdCounter.getAndIncrement();
        String technicalId = "subscriber-" + id;
        subscriber.setSubscribedAt(java.time.Instant.now().toString());
        subscriberCache.put(technicalId, subscriber);
        log.info("Subscriber created with technicalId={}", technicalId);
        processSubscriber(technicalId, subscriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<List<Subscriber>> getSubscribers(@RequestParam(required = false) String petType) {
        List<Subscriber> subscribers = new ArrayList<>();
        for (Subscriber subscriber : subscriberCache.values()) {
            if (petType == null || petType.isBlank() || (subscriber.getPreferredPetTypes() != null && subscriber.getPreferredPetTypes().contains(petType))) {
                subscribers.add(subscriber);
            }
        }
        return ResponseEntity.ok(subscribers);
    }

    // --- PROCESS METHODS ---

    private void processJob(String technicalId, Job job) {
        log.info("Processing Job with technicalId={} and jobType={}", technicalId, job.getJobType());
        // Simulate validation criteria
        if (!simulateJobTypeCriteria(job.getJobType())) {
            job.setStatus("FAILED");
            log.error("Job {} failed validation due to invalid jobType", technicalId);
            return;
        }
        if (job.getScheduledAt() == null || job.getScheduledAt().isBlank()) {
            job.setStatus("FAILED");
            log.error("Job {} failed validation due to missing scheduledAt", technicalId);
            return;
        }
        job.setStatus("RUNNING");
        if ("INGESTION".equalsIgnoreCase(job.getJobType())) {
            simulatePetstoreApiIngestion();
            // Simulate ingestion of pets - create sample pets and process them
            Pet pet = new Pet();
            pet.setId(petIdCounter.getAndIncrement());
            pet.setName("Ingested Pet");
            Pet.Category category = new Pet.Category();
            category.setId(1L);
            category.setName("dog");
            pet.setCategory(category);
            pet.setPhotoUrls(List.of("https://example.com/photo1.jpg"));
            Pet.Tag tag = new Pet.Tag();
            tag.setId(1L);
            tag.setName("friendly");
            pet.setTags(List.of(tag));
            pet.setStatus("available");
            String petTechnicalId = "pet-" + pet.getId();
            petCache.put(petTechnicalId, pet);
            log.info("Pet ingested with technicalId={}", petTechnicalId);
            processPet(petTechnicalId, pet);
        } else if ("NOTIFICATION".equalsIgnoreCase(job.getJobType())) {
            // Simulate fetching subscribers and pets and sending notifications
            simulateFetchSubscribersAndPets();
            simulateSendNotifications();
        }
        job.setStatus("COMPLETED");
        log.info("Job with technicalId={} completed", technicalId);
    }

    private boolean simulateJobTypeCriteria(String jobType) {
        return "INGESTION".equalsIgnoreCase(jobType) || "NOTIFICATION".equalsIgnoreCase(jobType);
    }

    private void simulatePetstoreApiIngestion() {
        log.info("Simulating Petstore API ingestion...");
        // Simulate delay or API call
    }

    private void simulateFetchSubscribersAndPets() {
        log.info("Simulating fetching subscribers and pets for notifications...");
        // Simulate data retrieval
    }

    private void simulateSendNotifications() {
        log.info("Simulating sending notifications to subscribers...");
        // Simulate notification sending
    }

    private void processPet(String technicalId, Pet pet) {
        log.info("Processing Pet with technicalId={}", technicalId);
        simulatePetValidation();
        // Example validation logic
        if (pet.getName() == null || pet.getName().isBlank() || pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet {} validation failed: missing name or status", technicalId);
            return;
        }
        log.info("Pet {} processed and stored successfully", technicalId);
    }

    private void simulatePetValidation() {
        log.info("Simulating Pet validation...");
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber with technicalId={}", technicalId);
        simulateSubscriberValidation();
        // Example validation logic for email format
        if (subscriber.getEmail() == null || subscriber.getEmail().isBlank() || !subscriber.getEmail().contains("@")) {
            log.error("Subscriber {} validation failed: invalid email", technicalId);
            return;
        }
        if (subscriber.getPreferredPetTypes() == null || subscriber.getPreferredPetTypes().isEmpty()) {
            log.error("Subscriber {} validation failed: no preferred pet types", technicalId);
            return;
        }
        log.info("Subscriber {} processed and ready for notifications", technicalId);
    }

    private void simulateSubscriberValidation() {
        log.info("Simulating Subscriber validation...");
    }
}