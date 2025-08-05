package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Pet;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/workflows - create Workflow entity
    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (workflow == null || !workflow.isValid()) {
            log.error("Invalid Workflow entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String technicalId = String.valueOf(workflowIdCounter.getAndIncrement());
        workflow.setStatus("PENDING");
        workflow.setCreatedAt(java.time.Instant.now().toString());
        workflowCache.put(technicalId, workflow);
        log.info("Workflow created with technicalId {}", technicalId);

        // Trigger processing synchronously here
        try {
            processWorkflow(technicalId, workflow);
        } catch (Exception e) {
            log.error("Error processing Workflow {}", technicalId, e);
            workflow.setStatus("FAILED");
            workflowCache.put(technicalId, workflow);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/workflows/{id} - get Workflow by technicalId
    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String id) {
        Workflow workflow = workflowCache.get(id);
        if (workflow == null) {
            log.error("Workflow not found with technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(workflow);
    }

    // GET /prototype/pets/{id} - get Pet by technicalId
    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    // GET /prototype/pets - optional get pets by category query param
    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPetsByCategory(@RequestParam(required = false) String category) {
        List<Pet> result = new ArrayList<>();
        for (Pet pet : petCache.values()) {
            if (category == null || category.isBlank() || category.equalsIgnoreCase(pet.getCategory())) {
                result.add(pet);
            }
        }
        return ResponseEntity.ok(result);
    }

    // Process Workflow: fetch pets from Petstore API by petCategory, save Pet entities immutably
    private void processWorkflow(String technicalId, Workflow workflow) {
        log.info("Processing Workflow {} with petCategory {}", technicalId, workflow.getPetCategory());

        // Validate required fields again before processing
        if (workflow.getPetCategory() == null || workflow.getPetCategory().isBlank()) {
            log.error("Workflow petCategory is missing or blank");
            workflow.setStatus("FAILED");
            workflowCache.put(technicalId, workflow);
            return;
        }

        // Simulate calling external Petstore API to fetch pets by category
        List<Pet> fetchedPets = fetchPetsFromPetstoreApi(workflow.getPetCategory());
        if (fetchedPets == null) {
            log.error("Failed to fetch pets from Petstore API");
            workflow.setStatus("FAILED");
            workflowCache.put(technicalId, workflow);
            return;
        }

        // Save each pet immutably
        for (Pet pet : fetchedPets) {
            String petTechnicalId = String.valueOf(petIdCounter.getAndIncrement());
            pet.setCreatedAt(java.time.Instant.now().toString());
            petCache.put(petTechnicalId, pet);
            try {
                processPet(petTechnicalId, pet);
            } catch (Exception e) {
                log.error("Error processing Pet {}", petTechnicalId, e);
            }
        }

        workflow.setStatus("COMPLETED");
        workflowCache.put(technicalId, workflow);
        log.info("Workflow {} completed successfully", technicalId);
    }

    // Simulated external API call to Petstore API - returns list of pets for given category
    private List<Pet> fetchPetsFromPetstoreApi(String category) {
        // In a real app, this would be an HTTP call to Petstore API.
        // Here, we simulate with dummy data.

        List<Pet> pets = new ArrayList<>();
        if ("cats".equalsIgnoreCase(category)) {
            Pet p1 = new Pet();
            p1.setPetId(101L);
            p1.setName("Whiskers");
            p1.setCategory("cats");
            p1.setStatus("available");
            p1.setPhotoUrls("http://example.com/photo1.jpg,http://example.com/photo2.jpg");
            p1.setTags("playful,fluffy");
            pets.add(p1);

            Pet p2 = new Pet();
            p2.setPetId(102L);
            p2.setName("Snowball");
            p2.setCategory("cats");
            p2.setStatus("pending");
            p2.setPhotoUrls("http://example.com/photo3.jpg");
            p2.setTags("sleepy");
            pets.add(p2);
        } else if ("dogs".equalsIgnoreCase(category)) {
            Pet p1 = new Pet();
            p1.setPetId(201L);
            p1.setName("Buddy");
            p1.setCategory("dogs");
            p1.setStatus("available");
            p1.setPhotoUrls("http://example.com/dog1.jpg");
            p1.setTags("friendly,energetic");
            pets.add(p1);
        } else {
            // Return empty list for other categories
        }
        return pets;
    }

    // Process Pet: enrich or tag pet data, mark as processed
    private void processPet(String technicalId, Pet pet) {
        log.info("Processing Pet {} named {}", technicalId, pet.getName());
        // Example enrichment: add emoji tags based on category
        String tags = pet.getTags() != null ? pet.getTags() : "";
        if ("cats".equalsIgnoreCase(pet.getCategory())) {
            tags += (tags.isEmpty() ? "" : ",") + "😺";
        } else if ("dogs".equalsIgnoreCase(pet.getCategory())) {
            tags += (tags.isEmpty() ? "" : ",") + "🐶";
        }
        pet.setTags(tags);
        petCache.put(technicalId, pet);
        log.info("Pet {} processed with tags: {}", technicalId, pet.getTags());
    }
}