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
import com.java_template.application.entity.AdoptionEvent;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counter for Workflow (orchestration entity)
    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    // Cache and ID counter for AdoptionEvent
    private final ConcurrentHashMap<String, AdoptionEvent> adoptionEventCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionEventIdCounter = new AtomicLong(1);

    // Cache and ID counter for Pet (read-only, no POST endpoint)
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /prototype/workflows - create Workflow
    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (!workflow.isValid()) {
            log.error("Invalid Workflow entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "wf-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);
        log.info("Workflow created with technicalId: {}", technicalId);

        // Process workflow entity with business logic
        processWorkflow(technicalId, workflow);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/workflows/{id} - retrieve Workflow by technicalId
    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") String technicalId) {
        Workflow workflow = workflowCache.get(technicalId);
        if (workflow == null) {
            log.error("Workflow not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(workflow);
    }

    // POST /prototype/adoption-events - create AdoptionEvent
    @PostMapping("/adoption-events")
    public ResponseEntity<Map<String, String>> createAdoptionEvent(@RequestBody AdoptionEvent adoptionEvent) {
        if (!adoptionEvent.isValid()) {
            log.error("Invalid AdoptionEvent entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "ae-" + adoptionEventIdCounter.getAndIncrement();
        adoptionEventCache.put(technicalId, adoptionEvent);
        log.info("AdoptionEvent created with technicalId: {}", technicalId);

        // Process adoption event with business logic
        processAdoptionEvent(technicalId, adoptionEvent);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/adoption-events/{id} - retrieve AdoptionEvent by technicalId
    @GetMapping("/adoption-events/{id}")
    public ResponseEntity<AdoptionEvent> getAdoptionEvent(@PathVariable("id") String technicalId) {
        AdoptionEvent adoptionEvent = adoptionEventCache.get(technicalId);
        if (adoptionEvent == null) {
            log.error("AdoptionEvent not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(adoptionEvent);
    }

    // GET /prototype/pets/{id} - retrieve Pet by technicalId (read only)
    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            log.error("Pet not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/pets - no POST endpoint for Pet as per requirements (managed via other entities)

    // Business logic processing methods

    private void processWorkflow(String technicalId, Workflow workflow) {
        log.info("Processing Workflow with technicalId: {}", technicalId);

        // Step 1: Initial State
        workflow.setStatus("PENDING");

        // Step 2: Validate petCriteria format (simple check)
        if (workflow.getPetCriteria() == null || workflow.getPetCriteria().isBlank()) {
            workflow.setStatus("FAILED");
            log.error("Workflow petCriteria is invalid or blank");
            return;
        }

        try {
            // Step 3: Query Petstore API using petCriteria
            // For prototype, simulate pet query with dummy pets
            List<Pet> matchedPets = new ArrayList<>();

            // Simulate fetching pets - in real app use WebClient/RestTemplate
            Pet samplePet = new Pet();
            samplePet.setPetId(1L);
            samplePet.setName("Fluffy");
            samplePet.setCategory("Cat");
            samplePet.setPhotoUrls("http://example.com/cat1.jpg");
            samplePet.setTags("cute,friendly");
            samplePet.setStatus("available");

            matchedPets.add(samplePet);

            // Step 4: For each matched pet, create AdoptionEvent entity
            for (Pet pet : matchedPets) {
                AdoptionEvent adoptionEvent = new AdoptionEvent();
                adoptionEvent.setPetId(pet.getPetId());
                adoptionEvent.setAdopterName("SystemGenerated");
                adoptionEvent.setAdoptedAt(new Date().toString());
                adoptionEvent.setStory("Auto-created adoption event from workflow.");

                String adoptionEventId = "ae-" + adoptionEventIdCounter.getAndIncrement();
                adoptionEventCache.put(adoptionEventId, adoptionEvent);

                // Process each AdoptionEvent
                processAdoptionEvent(adoptionEventId, adoptionEvent);
            }

            // Step 5: Mark workflow as COMPLETED
            workflow.setStatus("COMPLETED");

        } catch (Exception ex) {
            workflow.setStatus("FAILED");
            log.error("Error processing workflow: {}", ex.getMessage());
        }
    }

    private void processAdoptionEvent(String technicalId, AdoptionEvent adoptionEvent) {
        log.info("Processing AdoptionEvent with technicalId: {}", technicalId);

        // Step 1: Validate adopterName and petId
        if (adoptionEvent.getAdopterName() == null || adoptionEvent.getAdopterName().isBlank()) {
            log.error("AdoptionEvent adopterName is invalid");
            return;
        }
        if (adoptionEvent.getPetId() == null) {
            log.error("AdoptionEvent petId is null");
            return;
        }

        try {
            // Step 2: Update pet status by creating a new Pet entity version (immutable)
            Pet pet = new Pet();
            pet.setPetId(adoptionEvent.getPetId());
            pet.setName("AdoptedPet"); // Placeholder; in real case fetch pet details
            pet.setCategory("Unknown");
            pet.setPhotoUrls("");
            pet.setTags("");
            pet.setStatus("pending");

            String petIdKey = "pet-" + pet.getPetId();
            petCache.put(petIdKey, pet);
            log.info("Pet status updated to pending for petId: {}", pet.getPetId());

            // Step 3: Mark adoption event as processed (could add a processed flag - omitted here)

            // Step 4: Notification or logging
            log.info("AdoptionEvent processed successfully for petId: {}", adoptionEvent.getPetId());

        } catch (Exception ex) {
            log.error("Error processing adoption event: {}", ex.getMessage());
        }
    }
}