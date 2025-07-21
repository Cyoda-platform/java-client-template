package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_MODEL_PETJOB = "PetJob";
    private static final String ENTITY_MODEL_PET = "Pet";
    // PetEvent kept local cache as minor entity, no refactoring for it as per instructions

    private final Map<String, PetEvent> petEventCache = new HashMap<>();
    private long petEventIdCounter = 1;

    // POST /controller/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@Valid @RequestBody PetJob petJob) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null) {
            log.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }
        petJob.setStatus("PENDING");
        UUID technicalId = entityService.addItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob).get();
        petJob.setTechnicalId(technicalId);
        log.info("Created PetJob with technicalId: {}", technicalId);

        try {
            // processPetJob removed
        } catch (Exception e) {
            log.error("Error processing PetJob with technicalId: {}", technicalId, e);
            petJob.setStatus("FAILED");
            entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, technicalId, petJob).get();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobTechnicalId", technicalId.toString(), "status", petJob.getStatus()));
    }

    // GET /controller/petjob/{technicalId} - get PetJob by technicalId
    @GetMapping("/petjob/{technicalId}")
    public ResponseEntity<?> getPetJob(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> future = entityService.getItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, uuid);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("PetJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = objectMapper.treeToValue(node, PetJob.class);
        return ResponseEntity.ok(petJob);
    }

    // POST /controller/pet - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getAge() == null || pet.getAge() < 0) {
            log.error("Invalid Pet creation request: missing or invalid fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid fields: name, species, age");
        }
        pet.setStatus("ACTIVE");
        UUID technicalId = entityService.addItem(ENTITY_MODEL_PET, ENTITY_VERSION, pet).get();
        pet.setTechnicalId(technicalId);
        log.info("Created Pet with technicalId: {}", technicalId);

        try {
            // processPet removed
        } catch (Exception e) {
            log.error("Error processing Pet with technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{technicalId} - get Pet by technicalId
    @GetMapping("/pet/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> future = entityService.getItem(ENTITY_MODEL_PET, ENTITY_VERSION, uuid);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("Pet not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /controller/petevent - create PetEvent entity (kept local cache)
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
                || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
                || petEvent.getEventTimestamp() == null) {
            log.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: petId, eventType, eventTimestamp");
        }
        String id = "event-" + petEventIdCounter++;
        petEvent.setId(id);
        petEvent.setTechnicalId(UUID.randomUUID());
        petEvent.setStatus("RECORDED");
        petEventCache.put(id, petEvent);
        log.info("Created PetEvent with ID: {}", id);

        try {
            // processPetEvent removed
        } catch (Exception e) {
            log.error("Error processing PetEvent with ID: {}", id, e);
            petEvent.setStatus("FAILED");
            petEventCache.put(id, petEvent);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetEvent");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /controller/petevent/{id} - get PetEvent by ID (local cache)
    @GetMapping("/petevent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }
}