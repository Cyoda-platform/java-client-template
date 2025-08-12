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

    // Cache and ID counter for Workflow entity (orchestration entity)
    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    // Cache and ID counter for Pet entity (business domain entity)
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // --- Workflow Endpoints ---

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (workflow == null || !workflow.isValid()) {
            log.error("Invalid Workflow entity data received.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Workflow data"));
        }
        String technicalId = "workflow-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);
        log.info("Created Workflow with technicalId: {}", technicalId);
        processWorkflow(technicalId, workflow);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<?> getWorkflowById(@PathVariable String technicalId) {
        Workflow workflow = workflowCache.get(technicalId);
        if (workflow == null) {
            log.error("Workflow not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
        }
        log.info("Retrieved Workflow with technicalId: {}", technicalId);
        return ResponseEntity.ok(workflow);
    }

    // --- Pet Endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet entity data received.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Pet data"));
        }
        String technicalId = "pet-" + petIdCounter.getAndIncrement();
        petCache.put(technicalId, pet);
        log.info("Created Pet with technicalId: {}", technicalId);
        processPet(technicalId, pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<?> getPetById(@PathVariable String technicalId) {
        Pet pet = petCache.get(technicalId);
        if (pet == null) {
            log.error("Pet not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        }
        log.info("Retrieved Pet with technicalId: {}", technicalId);
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPetsByStatus(@RequestParam(required = false) String status) {
        List<Pet> result = new ArrayList<>();
        if (status == null || status.isBlank()) {
            result.addAll(petCache.values());
        } else {
            for (Pet pet : petCache.values()) {
                if (status.equalsIgnoreCase(pet.getStatus())) {
                    result.add(pet);
                }
            }
        }
        log.info("Retrieved {} pets with status '{}'", result.size(), status);
        return ResponseEntity.ok(result);
    }

    // --- Processing Methods ---

    private void processWorkflow(String technicalId, Workflow workflow) {
        // Simulate processors and criteria per requirements

        log.info("Processing Workflow: {}", technicalId);

        runValidateInputDataCriterion(workflow);

        runProcessPetEntitiesProcessor(workflow);

        // Update workflow status to RUNNING during processing
        workflow.setStatus("RUNNING");
        // Simulate execution
        try {
            // Example: create related Pet entity based on inputData - simplistic simulation
            Pet newPet = new Pet();
            newPet.setName("GeneratedPetFromWorkflow");
            newPet.setCategory("Cat");
            newPet.setStatus("available");
            newPet.setPhotoUrls("http://example.com/photo.jpg");
            newPet.setTags("generated,workflow");

            String petTechnicalId = "pet-" + petIdCounter.getAndIncrement();
            petCache.put(petTechnicalId, newPet);
            log.info("Created Pet '{}' from Workflow '{}'", petTechnicalId, technicalId);

            // Update workflow status to COMPLETED after successful processing
            workflow.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Error processing Workflow '{}': {}", technicalId, e.getMessage());
            workflow.setStatus("FAILED");
        }

        log.info("Workflow {} status updated to {}", technicalId, workflow.getStatus());

        // Optionally notify external systems or log completion here
    }

    private void runValidateInputDataCriterion(Workflow workflow) {
        // Simulate validation logic for inputData
        if (workflow.getInputData() == null || workflow.getInputData().isBlank()) {
            log.error("Workflow inputData is invalid or missing.");
            throw new IllegalArgumentException("Workflow inputData is invalid");
        }
        log.info("Workflow inputData validation passed.");
    }

    private void runProcessPetEntitiesProcessor(Workflow workflow) {
        // Simulate processing related Pet entities based on workflow input
        log.info("Processing Pet entities for Workflow: {}", workflow.getName());
    }

    private void processPet(String technicalId, Pet pet) {
        log.info("Processing Pet: {}", technicalId);

        if (!checkPetStatusValid(pet.getStatus())) {
            log.error("Invalid Pet status: {}", pet.getStatus());
            return; // or handle error as needed
        }

        if (!checkPetCategoryExists(pet.getCategory())) {
            log.error("Pet category does not exist: {}", pet.getCategory());
            return; // or handle error as needed
        }

        runApplyTaggingProcessor(pet);

        runValidatePhotoUrlsProcessor(pet);

        // Pet data is stored immutably; no further updates unless requested
        log.info("Pet processing completed for {}", technicalId);
    }

    private boolean checkPetStatusValid(String status) {
        // Simulate status validation, e.g., available, pending, sold
        List<String> validStatuses = List.of("available", "pending", "sold");
        boolean isValid = validStatuses.contains(status.toLowerCase());
        if (!isValid) {
            log.error("Pet status '{}' is not valid", status);
        }
        return isValid;
    }

    private boolean checkPetCategoryExists(String category) {
        // Simulate category existence check, e.g., Cat, Dog
        List<String> validCategories = List.of("cat", "dog", "bird", "reptile");
        boolean exists = validCategories.contains(category.toLowerCase());
        if (!exists) {
            log.error("Pet category '{}' does not exist", category);
        }
        return exists;
    }

    private void runApplyTaggingProcessor(Pet pet) {
        // Simulate adding or verifying tags business logic
        if (pet.getTags() == null || pet.getTags().isBlank()) {
            pet.setTags("default");
            log.info("Tags were empty; set default tag.");
        } else {
            log.info("Pet tags verified: {}", pet.getTags());
        }
    }

    private void runValidatePhotoUrlsProcessor(Pet pet) {
        // Simulate validation of photo URLs format
        String urls = pet.getPhotoUrls();
        if (urls == null || urls.isBlank()) {
            log.error("Pet photoUrls are empty");
            return;
        }
        String[] urlArray = urls.split(",");
        for (String url : urlArray) {
            if (!url.trim().startsWith("http")) {
                log.error("Invalid photo URL: {}", url);
            }
        }
        log.info("Photo URLs validated for Pet.");
    }
}