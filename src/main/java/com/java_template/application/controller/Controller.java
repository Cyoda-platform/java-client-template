package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetUpdateJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
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
    private final ObjectMapper objectMapper;

    private static final String PET_UPDATE_JOB_ENTITY = "PetUpdateJob";
    private static final String PET_ENTITY = "Pet";

    // PetUpdateJob POST
    @PostMapping("/petUpdateJob")
    public ResponseEntity<?> createPetUpdateJob(@Valid @RequestBody PetUpdateJob job) throws JsonProcessingException {
        if (job == null) {
            log.error("PetUpdateJob creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body required");
        }
        if (job.getJobId() == null || job.getJobId().isBlank()) {
            job.setJobId("job-" + UUID.randomUUID());
        }
        if (job.getRequestedAt() == null) {
            job.setRequestedAt(LocalDateTime.now());
        }
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (!job.isValid()) {
            log.error("PetUpdateJob creation failed: validation failed for jobId {}", job.getJobId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetUpdateJob data");
        }
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(PET_UPDATE_JOB_ENTITY, ENTITY_VERSION, job);
            UUID technicalId = idFuture.join();
            log.info("Created PetUpdateJob with technicalId {}", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(job);
        } catch (Exception e) {
            log.error("Error creating PetUpdateJob: {}", e.getMessage());
            throw e;
        }
    }

    // PetUpdateJob GET
    @GetMapping("/petUpdateJob/{technicalId}")
    public ResponseEntity<?> getPetUpdateJob(@PathVariable String technicalId) throws JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        ObjectNode objNode = entityService.getItem(PET_UPDATE_JOB_ENTITY, ENTITY_VERSION, uuid).join();
        if (objNode == null) {
            log.error("PetUpdateJob not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateJob not found");
        }
        PetUpdateJob job = objectMapper.treeToValue(objNode, PetUpdateJob.class);
        return ResponseEntity.ok(job);
    }

    // Pet POST
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws JsonProcessingException {
        if (pet == null) {
            log.error("Pet creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body required");
        }
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            pet.setPetId("pet-" + UUID.randomUUID());
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }
        if (!pet.isValid()) {
            log.error("Pet creation failed: validation failed for petId {}", pet.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
            UUID technicalId = idFuture.join();
            log.info("Created Pet with technicalId {}", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error creating Pet: {}", e.getMessage());
            throw e;
        }
    }

    // Pet GET
    @GetMapping("/pet/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable String technicalId) throws JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        ObjectNode objNode = entityService.getItem(PET_ENTITY, ENTITY_VERSION, uuid).join();
        if (objNode == null) {
            log.error("Pet not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(objNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // PetEvent POST - minor entity, keep local cache behavior as is
    private final java.util.concurrent.ConcurrentHashMap<String, PetEvent> petEventCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong petEventIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent event) {
        if (event == null) {
            log.error("PetEvent creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body required");
        }
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId("event-" + petEventIdCounter.getAndIncrement());
        }
        if (event.getStatus() == null || event.getStatus().isBlank()) {
            event.setStatus("RECEIVED");
        }
        if (event.getEventTimestamp() == null) {
            event.setEventTimestamp(LocalDateTime.now());
        }
        if (!event.isValid()) {
            log.error("PetEvent creation failed: validation failed for eventId {}", event.getEventId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetEvent data");
        }
        petEventCache.put(event.getEventId(), event);
        log.info("Created PetEvent with eventId {}", event.getEventId());

        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    // PetEvent GET - minor entity, keep local cache behavior as is
    @GetMapping("/petEvent/{eventId}")
    public ResponseEntity<?> getPetEvent(@PathVariable String eventId) {
        PetEvent event = petEventCache.get(eventId);
        if (event == null) {
            log.error("PetEvent not found: {}", eventId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(event);
    }
}