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
import com.java_template.application.entity.AdoptionRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // WORKFLOW Endpoints

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (!workflow.isValid()) {
            log.error("Invalid Workflow entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "workflow_" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);
        log.info("Workflow created with id: {}", technicalId);

        processWorkflow(technicalId, workflow);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflowById(@PathVariable("id") String id) {
        Workflow workflow = workflowCache.get(id);
        if (workflow == null) {
            log.error("Workflow not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Workflow retrieved with id: {}", id);
        return ResponseEntity.ok(workflow);
    }

    // PET Endpoints

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        if (!pet.isValid()) {
            log.error("Invalid Pet entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "pet_" + petIdCounter.getAndIncrement();
        petCache.put(technicalId, pet);
        log.info("Pet created with id: {}", technicalId);

        processPet(technicalId, pet);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Pet retrieved with id: {}", id);
        return ResponseEntity.ok(pet);
    }

    // ADOPTION REQUEST Endpoints

    @PostMapping("/adoption-requests")
    public ResponseEntity<Map<String, String>> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) {
        if (!adoptionRequest.isValid()) {
            log.error("Invalid AdoptionRequest entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "adoptionRequest_" + adoptionRequestIdCounter.getAndIncrement();
        adoptionRequestCache.put(technicalId, adoptionRequest);
        log.info("AdoptionRequest created with id: {}", technicalId);

        processAdoptionRequest(technicalId, adoptionRequest);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/adoption-requests/{id}")
    public ResponseEntity<AdoptionRequest> getAdoptionRequestById(@PathVariable("id") String id) {
        AdoptionRequest adoptionRequest = adoptionRequestCache.get(id);
        if (adoptionRequest == null) {
            log.error("AdoptionRequest not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("AdoptionRequest retrieved with id: {}", id);
        return ResponseEntity.ok(adoptionRequest);
    }

    // Process Methods - Business Logic Implementation

    private void processWorkflow(String technicalId, Workflow workflow) {
        log.info("Processing Workflow with id: {}", technicalId);

        // Validation: Check pet exists and status is "available"
        Optional<Map.Entry<String, Pet>> petEntry = petCache.entrySet().stream()
                .filter(entry -> entry.getKey().equals(workflow.getPetId()))
                .findFirst();

        if (petEntry.isEmpty()) {
            log.error("Pet referenced by Workflow {} not found", technicalId);
            workflow.setStatus("FAILED");
            return;
        }
        Pet pet = petEntry.get().getValue();
        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            log.error("Pet {} is not available for Workflow {}", workflow.getPetId(), technicalId);
            workflow.setStatus("FAILED");
            return;
        }

        // Simulate triggering adoption request processing or other logic
        workflow.setStatus("COMPLETED");
        log.info("Workflow {} processing COMPLETED", technicalId);
    }

    private void processPet(String technicalId, Pet pet) {
        log.info("Processing Pet with id: {}", technicalId);

        // Validation already done, simulate enrichment or sync with Petstore API

        // Example: Log sync action (real external API call would be here)
        log.info("Syncing Pet {} data with external Petstore API", technicalId);

        // Mark processing as complete
        log.info("Pet {} processing COMPLETED", technicalId);
    }

    private void processAdoptionRequest(String technicalId, AdoptionRequest adoptionRequest) {
        log.info("Processing AdoptionRequest with id: {}", technicalId);

        // Validation: Check if pet exists and is available
        Pet pet = petCache.get(adoptionRequest.getPetId());
        if (pet == null) {
            log.error("Pet {} referenced in AdoptionRequest {} not found", adoptionRequest.getPetId(), technicalId);
            adoptionRequest.setStatus("REJECTED");
            return;
        }
        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            log.error("Pet {} is not available for AdoptionRequest {}", adoptionRequest.getPetId(), technicalId);
            adoptionRequest.setStatus("REJECTED");
            return;
        }

        // Simulate updating pet status by creating a new Pet entity version (immutable)
        Pet newPetVersion = new Pet();
        newPetVersion.setName(pet.getName());
        newPetVersion.setCategory(pet.getCategory());
        newPetVersion.setPhotoUrls(pet.getPhotoUrls());
        newPetVersion.setTags(pet.getTags());
        newPetVersion.setCreatedAt(pet.getCreatedAt());
        newPetVersion.setStatus("pending");

        String newPetTechnicalId = "pet_" + petIdCounter.getAndIncrement();
        petCache.put(newPetTechnicalId, newPetVersion);
        log.info("Created new Pet version {} with status 'pending' due to AdoptionRequest {}", newPetTechnicalId, technicalId);

        adoptionRequest.setStatus("APPROVED");
        log.info("AdoptionRequest {} APPROVED", technicalId);
    }
}