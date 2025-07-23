package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetUpdateEvent;
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
import java.util.UUID;
import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // Keep petUpdateEventCache and counter locally as PetUpdateEvent has minor logic only
    private final Map<String, PetUpdateEvent> petUpdateEventCache = new HashMap<>();
    private final AtomicLong petUpdateEventIdCounter = new AtomicLong(1);

    private final AtomicLong petIdCounter = new AtomicLong(1); // for Pet id generation in update event

    // ------------------- PetJob Endpoints -------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws Exception {
        if (petJob == null || petJob.getJobId() == null || petJob.getJobId().isBlank()) {
            log.error("PetJob creation failed: Missing or blank jobId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("jobId is required");
        }

        petJob.setStatus("PENDING");
        petJob.setSubmittedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetJob",
                ENTITY_VERSION,
                petJob
        );
        UUID technicalId = idFuture.get();
        petJob.setId(technicalId.toString()); // set id from technicalId

        processPetJob(petJob);
        log.info("PetJob created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid PetJob id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob id format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "PetJob",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null) {
            log.error("PetJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }

        PetJob petJob = entityService.getObjectMapper().treeToValue(node, PetJob.class);
        petJob.setId(node.get("technicalId").asText());
        return ResponseEntity.ok(petJob);
    }

    // ------------------- Pet Endpoints -------------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws Exception {
        if (pet == null) {
            log.error("Pet creation failed: Request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: Missing or blank name");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
            log.error("Pet creation failed: Missing or blank species");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet species is required");
        }

        pet.setStatus("ACTIVE");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Pet",
                ENTITY_VERSION,
                pet
        );
        UUID technicalId = idFuture.get();
        pet.setId(technicalId.toString());

        processPet(pet);
        log.info("Pet created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Pet id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Pet",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null) {
            log.error("Pet not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        Pet pet = entityService.getObjectMapper().treeToValue(node, Pet.class);
        pet.setId(node.get("technicalId").asText());
        return ResponseEntity.ok(pet);
    }

    // ------------------- PetUpdateEvent Endpoints -------------------

    @PostMapping("/pets/update")
    public ResponseEntity<?> createPetUpdateEvent(@RequestBody PetUpdateEvent petUpdateEvent) {
        if (petUpdateEvent == null) {
            log.error("PetUpdateEvent creation failed: Request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetUpdateEvent data is required");
        }
        if (petUpdateEvent.getPetId() == null || petUpdateEvent.getPetId().isBlank()) {
            log.error("PetUpdateEvent creation failed: Missing or blank petId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required");
        }
        if (petUpdateEvent.getUpdatedFields() == null || petUpdateEvent.getUpdatedFields().isEmpty()) {
            log.error("PetUpdateEvent creation failed: updatedFields is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("updatedFields are required");
        }
        String id = "event-" + petUpdateEventIdCounter.getAndIncrement();
        petUpdateEvent.setId(id);
        petUpdateEvent.setStatus("PENDING");
        petUpdateEventCache.put(id, petUpdateEvent);

        processPetUpdateEvent(petUpdateEvent);

        log.info("PetUpdateEvent created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(petUpdateEvent);
    }

    @GetMapping("/pets/update/{id}")
    public ResponseEntity<?> getPetUpdateEvent(@PathVariable String id) {
        PetUpdateEvent petUpdateEvent = petUpdateEventCache.get(id);
        if (petUpdateEvent == null) {
            log.error("PetUpdateEvent not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateEvent not found");
        }
        return ResponseEntity.ok(petUpdateEvent);
    }

    // ------------------- Process Methods -------------------

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        if (petJob.getJobId() == null || petJob.getJobId().isBlank()) {
            petJob.setStatus("FAILED");
            log.error("PetJob validation failed: jobId is blank");
            return;
        }

        try {
            log.info("PetJob {} processing started", petJob.getJobId());
            petJob.setStatus("COMPLETED");
            log.info("PetJob {} processing completed successfully", petJob.getJobId());

            // Update PetJob status immutably: create new version with updated status
            PetJob updatedJob = new PetJob();
            updatedJob.setJobId(petJob.getJobId());
            updatedJob.setStatus(petJob.getStatus());
            updatedJob.setSubmittedAt(petJob.getSubmittedAt());
            // preserve other fields if any

            entityService.addItem("PetJob", ENTITY_VERSION, updatedJob).get();

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            log.error("PetJob processing failed: {}", e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getId());

        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getSpecies() == null || pet.getSpecies().isBlank()) {
            log.error("Pet validation failed: name or species is blank");
            return;
        }

        log.info("Pet {} persisted with status: {}", pet.getId(), pet.getStatus());
    }

    private void processPetUpdateEvent(PetUpdateEvent petUpdateEvent) {
        log.info("Processing PetUpdateEvent with ID: {}", petUpdateEvent.getId());

        if (petUpdateEvent.getPetId() == null || petUpdateEvent.getPetId().isBlank()) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent validation failed: petId is blank");
            return;
        }
        if (petUpdateEvent.getUpdatedFields() == null || petUpdateEvent.getUpdatedFields().isEmpty()) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent validation failed: updatedFields empty");
            return;
        }

        UUID petTechnicalId;
        try {
            petTechnicalId = UUID.fromString(petUpdateEvent.getPetId());
        } catch (IllegalArgumentException e) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent processing failed: invalid petId format");
            return;
        }

        Pet existingPet;
        try {
            CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem("Pet", ENTITY_VERSION, petTechnicalId);
            ObjectNode petNode = petNodeFuture.get();
            if (petNode == null) {
                petUpdateEvent.setStatus("FAILED");
                log.error("PetUpdateEvent processing failed: referenced Pet not found");
                return;
            }
            existingPet = entityService.getObjectMapper().treeToValue(petNode, Pet.class);
            existingPet.setId(petNode.get("technicalId").asText());
        } catch (Exception e) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent processing failed: error fetching Pet - {}", e.getMessage());
            return;
        }

        Pet updatedPet = new Pet();
        updatedPet.setId(null); // id will be assigned by entityService on addItem
        updatedPet.setName(existingPet.getName());
        updatedPet.setSpecies(existingPet.getSpecies());
        updatedPet.setBreed(existingPet.getBreed());
        updatedPet.setAge(existingPet.getAge());
        updatedPet.setStatus(existingPet.getStatus());

        Map<String, Object> updates = petUpdateEvent.getUpdatedFields();
        if (updates.containsKey("name")) {
            Object nameVal = updates.get("name");
            if (nameVal instanceof String && !((String) nameVal).isBlank()) {
                updatedPet.setName((String) nameVal);
            }
        }
        if (updates.containsKey("species")) {
            Object speciesVal = updates.get("species");
            if (speciesVal instanceof String && !((String) speciesVal).isBlank()) {
                updatedPet.setSpecies((String) speciesVal);
            }
        }
        if (updates.containsKey("breed")) {
            Object breedVal = updates.get("breed");
            if (breedVal instanceof String) {
                updatedPet.setBreed((String) breedVal);
            }
        }
        if (updates.containsKey("age")) {
            Object ageVal = updates.get("age");
            if (ageVal instanceof Number) {
                updatedPet.setAge(((Number) ageVal).intValue());
            }
        }
        if (updates.containsKey("status")) {
            Object statusVal = updates.get("status");
            if (statusVal instanceof String && !((String) statusVal).isBlank()) {
                updatedPet.setStatus((String) statusVal);
            }
        }

        try {
            CompletableFuture<UUID> addPetFuture = entityService.addItem("Pet", ENTITY_VERSION, updatedPet);
            UUID newPetId = addPetFuture.get();
            updatedPet.setId(newPetId.toString());

            petUpdateEvent.setStatus("PROCESSED");
            petUpdateEventCache.put(petUpdateEvent.getId(), petUpdateEvent);
            log.info("PetUpdateEvent {} processed successfully, created Pet version {}", petUpdateEvent.getId(), updatedPet.getId());
        } catch (Exception e) {
            petUpdateEvent.setStatus("FAILED");
            log.error("PetUpdateEvent processing failed: error saving updated Pet - {}", e.getMessage());
        }
    }
}