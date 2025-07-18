package com.java_template.prototype;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetUpdateJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // Local cache and ID counter only for minor/utility entities (none here per instructions)

    private final AtomicLong petUpdateJobIdCounter = new AtomicLong(1); // for generating IDs in pet update job processing
    private final AtomicLong petIdCounter = new AtomicLong(1); // for generating IDs in pet processing

    // --- PetUpdateJob Endpoints ---

    @PostMapping("/petUpdateJob")
    public ResponseEntity<?> createPetUpdateJob(@RequestBody PetUpdateJob jobRequest) {
        if (jobRequest == null || jobRequest.getSource() == null || jobRequest.getSource().isBlank()) {
            log.error("Invalid PetUpdateJob creation request: missing source");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Source is required");
        }

        jobRequest.setJobId(null); // clear any existing IDs to avoid confusion
        jobRequest.setId(null);
        jobRequest.setStatus("PENDING");
        jobRequest.setRequestedAt(LocalDateTime.now());

        CompletableFuture<java.util.UUID> idFuture = entityService.addItem("PetUpdateJob", ENTITY_VERSION, jobRequest);

        java.util.UUID technicalId;
        try {
            technicalId = idFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create PetUpdateJob: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        // Assign generated technicalId as string ID to jobRequest for tracking
        String generatedId = technicalId.toString();
        jobRequest.setId(generatedId);
        jobRequest.setJobId(generatedId);

        log.info("Created PetUpdateJob with technicalId: {}", generatedId);
        processPetUpdateJob(jobRequest);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", generatedId);
        response.put("status", jobRequest.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/petUpdateJob/{id}")
    public ResponseEntity<?> getPetUpdateJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        // Build condition to search by technicalId
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", id));

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("PetUpdateJob", ENTITY_VERSION, condition);

        ArrayNode items = itemsFuture.get();
        if (items == null || items.size() == 0) {
            log.error("PetUpdateJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateJob not found");
        }
        ObjectNode jobNode = (ObjectNode) items.get(0);
        return ResponseEntity.ok(jobNode);
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet petRequest) {
        if (petRequest == null
                || petRequest.getName() == null || petRequest.getName().isBlank()
                || petRequest.getCategory() == null || petRequest.getCategory().isBlank()
                || petRequest.getStatus() == null || petRequest.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name, category, and status are required");
        }

        petRequest.setId(null);
        petRequest.setPetId(null);

        CompletableFuture<java.util.UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, petRequest);

        java.util.UUID technicalId;
        try {
            technicalId = idFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create Pet: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        String generatedId = technicalId.toString();
        petRequest.setId(generatedId);
        petRequest.setPetId(generatedId);

        log.info("Created Pet with technicalId: {}", generatedId);
        processPet(petRequest);

        Map<String, String> response = new HashMap<>();
        response.put("petId", generatedId);
        response.put("status", petRequest.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", id));

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition);

        ArrayNode items = itemsFuture.get();
        if (items == null || items.size() == 0) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        ObjectNode petNode = (ObjectNode) items.get(0);
        return ResponseEntity.ok(petNode);
    }

    @GetMapping("/pet")
    public ResponseEntity<?> getAllPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("Pet", ENTITY_VERSION);
        ArrayNode pets = itemsFuture.get();
        return ResponseEntity.ok(pets);
    }

    // --- Process methods implementing business logic ---

    private void processPetUpdateJob(PetUpdateJob job) {
        log.info("Processing PetUpdateJob with technicalId: {}", job.getId());

        try {
            job.setStatus("PROCESSING");

            // Update job status in entityService as immutable pattern: create new version with updated status
            try {
                entityService.addItem("PetUpdateJob", ENTITY_VERSION, job).get();
            } catch (Exception e) {
                log.error("Failed to update PetUpdateJob status to PROCESSING: {}", e.getMessage());
            }

            // Simulate fetching data from Petstore API and creating Pet entities
            // Create dummy Pet as in prototype

            Pet dummyPet = new Pet();
            dummyPet.setName("Simulated Pet");
            dummyPet.setCategory("Cat");
            dummyPet.setStatus("AVAILABLE");
            dummyPet.setTags(Arrays.asList("simulated", "cat"));
            dummyPet.setPhotoUrls(Arrays.asList("http://example.com/simulated.jpg"));

            // Add dummy pet via entityService
            CompletableFuture<java.util.UUID> petIdFuture = entityService.addItem("Pet", ENTITY_VERSION, dummyPet);
            java.util.UUID petTechnicalId = petIdFuture.get();
            String petId = petTechnicalId.toString();
            dummyPet.setId(petId);
            dummyPet.setPetId(petId);

            processPet(dummyPet);

            job.setStatus("COMPLETED");
            try {
                entityService.addItem("PetUpdateJob", ENTITY_VERSION, job).get();
            } catch (Exception e) {
                log.error("Failed to update PetUpdateJob status to COMPLETED: {}", e.getMessage());
            }

            log.info("PetUpdateJob {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            try {
                entityService.addItem("PetUpdateJob", ENTITY_VERSION, job).get();
            } catch (Exception ex) {
                log.error("Failed to update PetUpdateJob status to FAILED: {}", ex.getMessage());
            }
            log.error("Error processing PetUpdateJob {}: {}", job.getId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getId());

        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet name is invalid");
            return;
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Pet status is invalid");
            return;
        }

        if (pet.getTags() != null) {
            List<String> normalizedTags = new ArrayList<>();
            for (String tag : pet.getTags()) {
                normalizedTags.add(tag.toLowerCase());
            }
            pet.setTags(normalizedTags);
        }

        log.info("Pet {} processed successfully", pet.getId());
    }
}