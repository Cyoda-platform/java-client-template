package com.java_template.prototype;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Subscriber;
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

    // Cache and ID counter for Job entity
    private final ConcurrentHashMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    // Cache and ID counter for Pet entity
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and ID counter for Subscriber entity
    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // --- Job Endpoints ---

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Job job) {
        if (!job.isValid()) {
            log.error("Invalid Job entity submitted");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "job-" + jobIdCounter.getAndIncrement();
        jobCache.put(technicalId, job);
        log.info("Created Job with technicalId: {}", technicalId);

        processJob(technicalId, job);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable("id") String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // --- Pet Endpoints ---

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    // Pets are created internally during Job processing, no POST endpoint exposed

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        if (!subscriber.isValid()) {
            log.error("Invalid Subscriber entity submitted");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "sub-" + subscriberIdCounter.getAndIncrement();
        subscriberCache.put(technicalId, subscriber);
        log.info("Created Subscriber with technicalId: {}", technicalId);

        processSubscriber(technicalId, subscriber);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable("id") String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // --- Processing Methods ---

    private void processJob(String technicalId, Job job) {
        log.info("Processing Job with ID: {}", technicalId);

        if (!job.isValid()) {
            log.error("Job {} is invalid", technicalId);
            return;
        }

        // Simulate ingestion of pet data and creation of Pet entities
        List<Pet> ingestedPets = simulatePetDataIngestion();

        for (Pet pet : ingestedPets) {
            String petTechnicalId = "pet-" + petIdCounter.getAndIncrement();
            petCache.put(petTechnicalId, pet);
            log.info("Created Pet with technicalId: {}", petTechnicalId);

            processPet(petTechnicalId, pet);
        }

        // Notify Subscribers based on their preferences
        notifySubscribers(ingestedPets);

        // Update job status to COMPLETED (for simplicity)
        job.setStatus("COMPLETED");
        jobCache.put(technicalId, job);

        log.info("Job {} processing completed", technicalId);
    }

    private void processPet(String technicalId, Pet pet) {
        log.info("Processing Pet with ID: {}", technicalId);

        if (!pet.isValid()) {
            log.error("Pet {} is invalid", technicalId);
            return;
        }
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", technicalId);

        if (!subscriber.isValid()) {
            log.error("Subscriber {} is invalid", technicalId);
            return;
        }
    }

    private List<Pet> simulatePetDataIngestion() {
        List<Pet> pets = new ArrayList<>();
        pets.add(new Pet("001", "Whiskers", "cat", "available"));
        pets.add(new Pet("002", "Fido", "dog", "sold"));
        return pets;
    }

    private void notifySubscribers(List<Pet> pets) {
        for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
            Subscriber subscriber = entry.getValue();
            Set<String> preferredTypes = new HashSet<>(Arrays.asList(subscriber.getPreferredPetTypes().split(",")));
            List<Pet> matchedPets = new ArrayList<>();
            for (Pet pet : pets) {
                if (preferredTypes.contains(pet.getSpecies())) {
                    matchedPets.add(pet);
                }
            }
            if (!matchedPets.isEmpty()) {
                log.info("Notifying subscriber {} about pets: {}", subscriber.getEmail(), matchedPets);
            }
        }
    }
}