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

    // POST /prototype/workflow - create new Workflow entity
    @PostMapping("/workflow")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        // Generate technicalId as string
        String technicalId = String.valueOf(workflowIdCounter.getAndIncrement());
        // Set creation timestamp and initial status
        if (workflow.getCreatedAt() == null || workflow.getCreatedAt().isBlank()) {
            workflow.setCreatedAt(java.time.Instant.now().toString());
        }
        if (workflow.getStatus() == null || workflow.getStatus().isBlank()) {
            workflow.setStatus("PENDING");
        }

        if (!workflow.isValid()) {
            log.error("Invalid Workflow entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        workflowCache.put(technicalId, workflow);
        log.info("Workflow entity saved with technicalId {}", technicalId);

        // Trigger processing
        try {
            processWorkflow(technicalId, workflow);
            log.info("Workflow processing completed for technicalId {}", technicalId);
        } catch (Exception e) {
            log.error("Error during Workflow processing for technicalId {}: {}", technicalId, e.getMessage());
            workflow.setStatus("FAILED");
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/workflow/{id} - retrieve Workflow entity by technicalId
    @GetMapping("/workflow/{id}")
    public ResponseEntity<Workflow> getWorkflowById(@PathVariable("id") String technicalId) {
        Workflow workflow = workflowCache.get(technicalId);
        if (workflow == null) {
            log.error("Workflow not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(workflow);
    }

    // POST /prototype/pet - create new Pet entity (optional manual add)
    @PostMapping("/pet")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        String technicalId = String.valueOf(petIdCounter.getAndIncrement());

        if (!pet.isValid()) {
            log.error("Invalid Pet entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        petCache.put(technicalId, pet);
        log.info("Pet entity saved with technicalId {}", technicalId);

        try {
            processPet(technicalId, pet);
            log.info("Pet processing completed for technicalId {}", technicalId);
        } catch (Exception e) {
            log.error("Error during Pet processing for technicalId {}: {}", technicalId, e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/pet/{id} - retrieve Pet entity by technicalId
    @GetMapping("/pet/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            log.error("Pet not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    // Business logic for processing Workflow entity
    private void processWorkflow(String technicalId, Workflow workflow) {
        // Validate petCategory and petStatus (already validated by isValid)
        // Query Petstore API to fetch pets filtered by category and status
        // For prototype, simulate fetching pets with dummy data

        List<Pet> fetchedPets = fetchPetsFromPetstoreApi(workflow.getPetCategory(), workflow.getPetStatus());

        // For each pet fetched, create immutable pet entity and store in cache
        for (Pet pet : fetchedPets) {
            String petTechId = String.valueOf(petIdCounter.getAndIncrement());

            petCache.put(petTechId, pet);
            log.info("Pet entity created during Workflow processing with technicalId {}", petTechId);

            // Process each pet
            processPet(petTechId, pet);
        }

        // Mark workflow as COMPLETED
        workflow.setStatus("COMPLETED");
        workflowCache.put(technicalId, workflow);
        log.info("Workflow {} marked as COMPLETED", technicalId);
    }

    // Business logic for processing Pet entity
    private void processPet(String technicalId, Pet pet) {
        // Validate pet fields (already validated by isValid)
        // Enrich pet data, e.g., add fun tags or descriptions (simulate)
        if (pet.getTags() == null || pet.getTags().isBlank()) {
            pet.setTags("fun, adorable");
        } else {
            pet.setTags(pet.getTags() + ", fun");
        }

        // Mark pet processing completed by updating status (simulate)
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("available");
        }

        petCache.put(technicalId, pet);
        log.info("Processed pet entity with technicalId {}", technicalId);
    }

    // Simulate external Petstore API call to fetch pets by category and status
    private List<Pet> fetchPetsFromPetstoreApi(String category, String status) {
        log.info("Fetching pets from Petstore API for category '{}' and status '{}'", category, status);

        // Simulated dummy pets list
        List<Pet> pets = new ArrayList<>();

        Pet pet1 = new Pet();
        pet1.setPetId(101L);
        pet1.setName("Fluffy");
        pet1.setCategory(category);
        pet1.setStatus(status);
        pet1.setTags("cute,furry");
        pet1.setPhotoUrls("https://example.com/photos/fluffy1.jpg");

        Pet pet2 = new Pet();
        pet2.setPetId(102L);
        pet2.setName("Buddy");
        pet2.setCategory(category);
        pet2.setStatus(status);
        pet2.setTags("friendly,playful");
        pet2.setPhotoUrls("https://example.com/photos/buddy1.jpg");

        pets.add(pet1);
        pets.add(pet2);

        return pets;
    }
}