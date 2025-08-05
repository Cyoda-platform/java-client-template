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

    private final ConcurrentHashMap<String, AdoptionJob> adoptionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, FunFact> funFactCache = new ConcurrentHashMap<>();
    private final AtomicLong funFactIdCounter = new AtomicLong(1);

    // AdoptionJob POST endpoint - orchestration entity creation
    @PostMapping("/adoption-jobs")
    public ResponseEntity<Map<String, String>> createAdoptionJob(@RequestBody AdoptionJob adoptionJob) {
        if (!adoptionJob.isValid()) {
            log.error("Invalid AdoptionJob entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "job-" + adoptionJobIdCounter.getAndIncrement();
        adoptionJobCache.put(technicalId, adoptionJob);
        log.info("AdoptionJob created with technicalId: {}", technicalId);

        processAdoptionJob(technicalId, adoptionJob);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // AdoptionJob GET by technicalId endpoint
    @GetMapping("/adoption-jobs/{id}")
    public ResponseEntity<AdoptionJob> getAdoptionJob(@PathVariable("id") String technicalId) {
        AdoptionJob adoptionJob = adoptionJobCache.get(technicalId);
        if (adoptionJob == null) {
            log.error("AdoptionJob not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(adoptionJob);
    }

    // Pet POST endpoint - business entity creation (usually via process)
    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        if (!pet.isValid()) {
            log.error("Invalid Pet entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "pet-" + petIdCounter.getAndIncrement();
        petCache.put(technicalId, pet);
        log.info("Pet created with technicalId: {}", technicalId);

        processPet(technicalId, pet);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Pet GET by technicalId
    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            log.error("Pet not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    // FunFact POST endpoint
    @PostMapping("/fun-facts")
    public ResponseEntity<Map<String, String>> createFunFact(@RequestBody FunFact funFact) {
        if (!funFact.isValid()) {
            log.error("Invalid FunFact entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "fact-" + funFactIdCounter.getAndIncrement();
        funFactCache.put(technicalId, funFact);
        log.info("FunFact created with technicalId: {}", technicalId);

        processFunFact(technicalId, funFact);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // FunFact GET by technicalId
    @GetMapping("/fun-facts/{id}")
    public ResponseEntity<FunFact> getFunFact(@PathVariable("id") String technicalId) {
        FunFact funFact = funFactCache.get(technicalId);
        if (funFact == null) {
            log.error("FunFact not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(funFact);
    }

    // Processing methods implementing business logic

    private void processAdoptionJob(String technicalId, AdoptionJob adoptionJob) {
        // Validate applicant email format (simple check)
        if (adoptionJob.getApplicantEmail() == null || adoptionJob.getApplicantEmail().isBlank() || !adoptionJob.getApplicantEmail().contains("@")) {
            adoptionJob.setStatus("REJECTED");
            log.error("AdoptionJob {} rejected due to invalid email", technicalId);
            return;
        }
        // Check pet availability
        Pet pet = null;
        for (Map.Entry<String, Pet> entry : petCache.entrySet()) {
            if (entry.getKey().equals("pet-" + adoptionJob.getPetId())) {
                pet = entry.getValue();
                break;
            }
        }
        if (pet == null) {
            adoptionJob.setStatus("REJECTED");
            log.error("AdoptionJob {} rejected due to pet not found", technicalId);
            return;
        }
        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            adoptionJob.setStatus("REJECTED");
            log.info("AdoptionJob {} rejected because pet {} is not available", technicalId, adoptionJob.getPetId());
            return;
        }
        // Approve adoption, update pet status to pending
        adoptionJob.setStatus("APPROVED");
        pet.setStatus("pending");
        log.info("AdoptionJob {} approved; pet {} status set to pending", technicalId, adoptionJob.getPetId());
    }

    private void processPet(String technicalId, Pet pet) {
        // Basic validation already done; enrich tags to lowercase trimmed
        if (pet.getTags() != null) {
            String[] tags = pet.getTags().split(",");
            List<String> normalizedTags = new ArrayList<>();
            for (String tag : tags) {
                normalizedTags.add(tag.trim().toLowerCase());
            }
            pet.setTags(String.join(",", normalizedTags));
        }
        log.info("Processed Pet {} with normalized tags", technicalId);
    }

    private void processFunFact(String technicalId, FunFact funFact) {
        if (funFact.getFactText() == null || funFact.getFactText().isBlank()) {
            log.error("FunFact {} invalid: empty factText", technicalId);
            return;
        }
        log.info("FunFact {} stored for category {}", technicalId, funFact.getPetCategory());
    }
}