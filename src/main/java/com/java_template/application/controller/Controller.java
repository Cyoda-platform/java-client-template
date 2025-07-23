package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String PET_JOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";

    // --- PetJob Endpoints ---

    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws JsonProcessingException {
        if (petJob == null || petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
            log.error("Invalid PetJob creation request: sourceUrl missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
        }

        petJob.setStatus("PENDING");
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_JOB_ENTITY, ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.join();
        petJob.setTechnicalId(technicalId);
        String generatedId = technicalId.toString();
        petJob.setId(generatedId);
        petJob.setJobId(generatedId);

        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_JOB_ENTITY, ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.join();
            if (item == null || item.isEmpty()) {
                log.error("PetJob not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
            }
            PetJob petJob = objectMapper.treeToValue(item, PetJob.class);
            return ResponseEntity.ok(petJob);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for PetJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob id format");
        }
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws JsonProcessingException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() ||
            pet.getCategory() == null || pet.getCategory().isBlank() ||
            pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name, category, and status are required");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.join();
        pet.setTechnicalId(technicalId);
        String generatedId = technicalId.toString();
        pet.setId(generatedId);
        pet.setPetId(generatedId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId);
            ObjectNode item = itemFuture.join();
            if (item == null || item.isEmpty()) {
                log.error("Pet not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            Pet pet = objectMapper.treeToValue(item, Pet.class);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format");
        }
    }

    // --- PetEvent Endpoints (minor entity, keep local cache) ---

    private final java.util.concurrent.ConcurrentHashMap<String, com.java_template.application.entity.PetEvent> petEventCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong petEventIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody com.java_template.application.entity.PetEvent petEvent) {
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

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        com.java_template.application.entity.PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }
}