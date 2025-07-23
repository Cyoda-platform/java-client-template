package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PET_JOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";

    // --- PetJob Endpoints ---

    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
            log.error("Invalid PetJob creation request: sourceUrl missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
        }

        petJob.setStatus("PENDING");
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(PET_JOB_ENTITY, ENTITY_VERSION, petJob);
            UUID technicalId = idFuture.join();
            // Set technicalId as id and jobId for response consistency
            String generatedId = technicalId.toString();
            petJob.setId(generatedId);
            petJob.setJobId(generatedId);

            processPetJob(petJob);

            return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
        } catch (Exception e) {
            log.error("Failed to create PetJob: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_JOB_ENTITY, ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.join();
            if (item == null || item.isEmpty()) {
                log.error("PetJob not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for PetJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob id format");
        }
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() ||
            pet.getCategory() == null || pet.getCategory().isBlank() ||
            pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name, category, and status are required");
        }

        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
            UUID technicalId = idFuture.join();
            String generatedId = technicalId.toString();
            pet.setId(generatedId);
            pet.setPetId(generatedId);

            processPet(pet);

            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Failed to create Pet: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.join();
            if (item == null || item.isEmpty()) {
                log.error("Pet not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format");
        }
    }

    // --- PetEvent Endpoints (minor entity, keep local cache) ---

    private final java.util.concurrent.ConcurrentHashMap<String, PetEvent> petEventCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong petEventIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null || petEvent.getEventType() == null || petEvent.getEventType().isBlank() ||
            petEvent.getPetId() == null || petEvent.getPetId().isBlank() ||
            petEvent.getStatus() == null || petEvent.getStatus().isBlank() ||
            petEvent.getTimestamp() == null) {
            log.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("eventType, petId, timestamp, and status are required");
        }

        String generatedId = "PE" + petEventIdCounter.getAndIncrement();
        petEvent.setId(generatedId);
        petEvent.setEventId(generatedId);
        petEventCache.put(generatedId, petEvent);

        processPetEvent(petEvent);

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

    // --- Process Methods ---

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        try {
            petJob.setStatus("PROCESSING");

            if (petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("Source URL is blank");
            }

            // Simulate creating Pet entities from fetched data
            Pet samplePet = new Pet();
            samplePet.setName("SampleCat");
            samplePet.setCategory("cat");
            samplePet.setStatus("AVAILABLE");

            CompletableFuture<UUID> petIdFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, samplePet);
            UUID petTechnicalId = petIdFuture.join();
            String petId = petTechnicalId.toString();
            samplePet.setId(petId);
            samplePet.setPetId(petId);

            processPet(samplePet);

            petJob.setStatus("COMPLETED");
            log.info("PetJob {} completed successfully", petJob.getId());
        } catch (Exception e) {
            petJob.setStatus("FAILED");
            log.error("PetJob {} failed: {}", petJob.getId(), e.getMessage());
        }

        try {
            UUID petJobTechId = UUID.fromString(petJob.getId());
            entityService.updateItem(PET_JOB_ENTITY, ENTITY_VERSION, petJobTechId, petJob).join();
        } catch (Exception e) {
            log.error("Failed to update PetJob status for id {}: {}", petJob.getId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());

        if (!pet.isValid()) {
            log.error("Pet validation failed for ID: {}", pet.getId());
            return;
        }

        if ("cat".equalsIgnoreCase(pet.getCategory())) {
            pet.setName(pet.getName() + " the Purrfect");
        }

        log.info("Pet {} is currently {}", pet.getId(), pet.getStatus());

        PetEvent petEvent = new PetEvent();
        petEvent.setId("PE" + petEventIdCounter.getAndIncrement());
        petEvent.setEventId(petEvent.getId());
        petEvent.setPetId(pet.getId());
        petEvent.setEventType("CREATED");
        petEvent.setTimestamp(LocalDateTime.now());
        petEvent.setStatus("RECORDED");
        petEventCache.put(petEvent.getId(), petEvent);

        processPetEvent(petEvent);
    }

    private void processPetEvent(PetEvent petEvent) {
        log.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (!petEvent.isValid()) {
            log.error("PetEvent validation failed for ID: {}", petEvent.getId());
            return;
        }

        log.info("Event {} for Pet {} of type {} recorded at {}", petEvent.getId(), petEvent.getPetId(), petEvent.getEventType(), petEvent.getTimestamp());
    }
}