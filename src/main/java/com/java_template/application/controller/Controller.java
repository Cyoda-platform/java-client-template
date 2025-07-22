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
import jakarta.validation.constraints.NotBlank;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String PETJOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";
    // PetEvent is minor entity, keep local cache for it
    private final Map<String, PetEvent> petEventCache = new HashMap<>();
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // POST /entity/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@Valid @RequestBody PetJob petJob) throws InterruptedException, ExecutionException, JsonProcessingException {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null) {
            log.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }
        petJob.setStatus("PENDING");
        CompletableFuture<UUID> idFuture = entityService.addItem(PETJOB_ENTITY, ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);
        log.info("Created PetJob with technicalId: {}", technicalId);

        try {
            // processPetJob method removed as per extraction
        } catch (Exception e) {
            log.error("Error processing PetJob with technicalId: {}", technicalId, e);
            petJob.setStatus("FAILED");
            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, technicalId, petJob).get();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobTechnicalId", technicalId.toString(), "status", petJob.getStatus()));
    }

    // GET /entity/petjob/{id} - get PetJob by technicalId string
    @GetMapping("/petjob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable @NotBlank String id) throws InterruptedException, ExecutionException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PETJOB_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("PetJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = objectMapper.treeToValue(item, PetJob.class);
        return ResponseEntity.ok(petJob);
    }

    // POST /entity/pet - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws InterruptedException, ExecutionException, JsonProcessingException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getAge() == null || pet.getAge() < 0) {
            log.error("Invalid Pet creation request: missing or invalid fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid fields: name, species, age");
        }
        pet.setStatus("ACTIVE");
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);
        log.info("Created Pet with technicalId: {}", technicalId);

        try {
            // processPet method removed as per extraction
        } catch (Exception e) {
            log.error("Error processing Pet with technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /entity/pet/{id} - get Pet by technicalId string
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable @NotBlank String id) throws InterruptedException, ExecutionException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(item, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /entity/petevent - create PetEvent entity (local cache kept)
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@Valid @RequestBody PetEvent petEvent) {
        if (petEvent == null || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
                || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
                || petEvent.getEventTimestamp() == null) {
            log.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: petId, eventType, eventTimestamp");
        }
        String id = "event-" + petEventIdCounter.getAndIncrement();
        petEvent.setId(id);
        petEvent.setTechnicalId(UUID.randomUUID());
        petEvent.setStatus("RECORDED");
        petEventCache.put(id, petEvent);
        log.info("Created PetEvent with ID: {}", id);

        try {
            // processPetEvent method removed as per extraction
        } catch (Exception e) {
            log.error("Error processing PetEvent with ID: {}", id, e);
            petEvent.setStatus("FAILED");
            petEventCache.put(id, petEvent);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetEvent");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /entity/petevent/{id} - get PetEvent by ID (local cache)
    @GetMapping("/petevent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable @NotBlank String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

}