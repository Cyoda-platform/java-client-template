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

        // 1. Validation: Check scheduleDetails format and completeness
        if (job.getScheduleDetails() == null || job.getScheduleDetails().isBlank()) {
            log.error("Job {} has invalid scheduleDetails", technicalId);
            return;
        }

        // 2. Processing: Simulate ingestion of pet data and creation of Pet entities
        List<Pet> ingestedPets = simulatePetDataIngestion();

        for (Pet pet : ingestedPets) {
            String petTechnicalId = "pet-" + petIdCounter.getAndIncrement();
            petCache.put(petTechnicalId, pet);
            log.info("Created Pet with technicalId: {}", petTechnicalId);

            processPet(petTechnicalId, pet);
        }

        // 3. Notify Subscribers based on their preferences
        notifySubscribers(ingestedPets);

        // 4. Update job status to COMPLETED (for simplicity)
        job.setStatus("COMPLETED");
        jobCache.put(technicalId, job);

        log.info("Job {} processing completed", technicalId);
    }

    private void processPet(String technicalId, Pet pet) {
        log.info("Processing Pet with ID: {}", technicalId);

        // Validation checkPetStatus
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet {} has invalid status", technicalId);
            return;
        }
        // Additional processing or logging can be added here
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", technicalId);

        if (subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            log.error("Subscriber {} has invalid email", technicalId);
            return;
        }
        if (subscriber.getPreferredPetTypes() == null || subscriber.getPreferredPetTypes().isBlank()) {
            log.error("Subscriber {} has no preferred pet types", technicalId);
            return;
        }
        // Additional processing or sending welcome notification can be added here
    }

    private List<Pet> simulatePetDataIngestion() {
        // Simulate data ingestion from external Petstore API
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

    // --- Entity Classes ---

    // These are simplified versions for the controller context

    public static class Job {
        private String jobId;
        private String scheduleDetails;
        private String status;

        public Job() {}

        public Job(String jobId, String scheduleDetails, String status) {
            this.jobId = jobId;
            this.scheduleDetails = scheduleDetails;
            this.status = status;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getScheduleDetails() {
            return scheduleDetails;
        }

        public void setScheduleDetails(String scheduleDetails) {
            this.scheduleDetails = scheduleDetails;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class Pet {
        private String id;
        private String name;
        private String species;
        private String status;

        public Pet() {}

        public Pet(String id, String name, String species, String status) {
            this.id = id;
            this.name = name;
            this.species = species;
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSpecies() {
            return species;
        }

        public void setSpecies(String species) {
            this.species = species;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return String.format("Pet{id='%s', name='%s', species='%s', status='%s'}", id, name, species, status);
        }
    }

    public static class Subscriber {
        private String email;
        private String preferredPetTypes;

        public Subscriber() {}

        public Subscriber(String email, String preferredPetTypes) {
            this.email = email;
            this.preferredPetTypes = preferredPetTypes;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPreferredPetTypes() {
            return preferredPetTypes;
        }

        public void setPreferredPetTypes(String preferredPetTypes) {
            this.preferredPetTypes = preferredPetTypes;
        }

        @Override
        public String toString() {
            return String.format("Subscriber{email='%s', preferredPetTypes='%s'}", email, preferredPetTypes);
        }
    }
}