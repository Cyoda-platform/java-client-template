package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // local counters for generating IDs in business logic (not stored)
    private final AtomicLong petRegistrationJobIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // POST /controller/petRegistrationJob - Create PetRegistrationJob
    @PostMapping("/petRegistrationJob")
    public ResponseEntity<?> createPetRegistrationJob(@RequestBody PetRegistrationJob job) throws ExecutionException, InterruptedException {
        if (job == null) {
            log.error("Received null PetRegistrationJob");
            return ResponseEntity.badRequest().body("PetRegistrationJob cannot be null");
        }
        // Prepare job: set status and generate jobId (business logic)
        String id = "job-" + petRegistrationJobIdCounter.getAndIncrement();
        job.setId(id);
        job.setJobId(id);
        job.setStatus("PENDING");

        if (!job.isValid()) {
            log.error("Invalid PetRegistrationJob: {}", job);
            return ResponseEntity.badRequest().body("Invalid PetRegistrationJob data");
        }

        // Add job via entityService
        CompletableFuture<UUID> idFuture = entityService.addItem("PetRegistrationJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        log.info("Created PetRegistrationJob with technicalId: {}", technicalId);

        // Retrieve saved job with technicalId field
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("PetRegistrationJob", ENTITY_VERSION, technicalId);
        ObjectNode savedJobNode = jobFuture.get();

        // process job asynchronously, using original job object (with assigned id)
        processPetRegistrationJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /controller/petRegistrationJob/{id} - Retrieve PetRegistrationJob by id (by technicalId)
    @GetMapping("/petRegistrationJob/{id}")
    public ResponseEntity<?> getPetRegistrationJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID for PetRegistrationJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        }
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("PetRegistrationJob", ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            log.error("PetRegistrationJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetRegistrationJob not found");
        }
        return ResponseEntity.ok(jobNode);
    }

    // POST /controller/pet - Create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null) {
            log.error("Received null Pet");
            return ResponseEntity.badRequest().body("Pet cannot be null");
        }
        // Prepare pet: generate petId and defaults
        String id = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(id);
        pet.setPetId(id);
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("ACTIVE");
        }
        if (pet.getRegisteredAt() == null || pet.getRegisteredAt().isBlank()) {
            pet.setRegisteredAt(Instant.now().toString());
        }
        if (!pet.isValid()) {
            log.error("Invalid Pet data: {}", pet);
            return ResponseEntity.badRequest().body("Invalid Pet data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        log.info("Created Pet with technicalId: {}", technicalId);

        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode savedPetNode = petFuture.get();

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{id} - Retrieve Pet by id (technicalId)
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        }
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(petNode);
    }

    // GET /controller/pet - List all Pets
    @GetMapping("/pet")
    public ResponseEntity<?> listPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> petsFuture = entityService.getItems("Pet", ENTITY_VERSION);
        ArrayNode petsArray = petsFuture.get();
        return ResponseEntity.ok(petsArray);
    }

    // POST /controller/petEvent - Create PetEvent
    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent event) throws ExecutionException, InterruptedException {
        if (event == null) {
            log.error("Received null PetEvent");
            return ResponseEntity.badRequest().body("PetEvent cannot be null");
        }
        // Prepare defaults and IDs
        String id = "event-" + petEventIdCounter.getAndIncrement();
        event.setId(id);
        event.setEventId(id);
        if (event.getStatus() == null || event.getStatus().isBlank()) {
            event.setStatus("RECORDED");
        }
        if (event.getEventTimestamp() == null || event.getEventTimestamp().isBlank()) {
            event.setEventTimestamp(Instant.now().toString());
        }
        if (!event.isValid()) {
            log.error("Invalid PetEvent data: {}", event);
            return ResponseEntity.badRequest().body("Invalid PetEvent data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("PetEvent", ENTITY_VERSION, event);
        UUID technicalId = idFuture.get();
        log.info("Created PetEvent with technicalId: {}", technicalId);

        CompletableFuture<ObjectNode> eventFuture = entityService.getItem("PetEvent", ENTITY_VERSION, technicalId);
        ObjectNode savedEventNode = eventFuture.get();

        processPetEvent(event);

        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    // GET /controller/petEvent/{id} - Retrieve PetEvent by id (technicalId)
    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID for PetEvent id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        }
        CompletableFuture<ObjectNode> eventFuture = entityService.getItem("PetEvent", ENTITY_VERSION, technicalId);
        ObjectNode eventNode = eventFuture.get();
        if (eventNode == null || eventNode.isEmpty()) {
            log.error("PetEvent not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(eventNode);
    }

    // Business logic for PetRegistrationJob
    private void processPetRegistrationJob(PetRegistrationJob job) throws ExecutionException, InterruptedException {
        log.info("Processing PetRegistrationJob with ID: {}", job.getId());
        if (!job.isValid()) {
            log.error("Invalid PetRegistrationJob during processing: {}", job);
            job.setStatus("FAILED");
            // Update job by adding new version instead of update (event-driven approach)
            entityService.addItem("PetRegistrationJob", ENTITY_VERSION, job).get();
            return;
        }
        // Create Pet entity from job
        Pet pet = new Pet();
        String petId = "pet-" + petIdCounter.getAndIncrement();
        pet.setPetId(petId);
        pet.setId(petId);
        pet.setName(job.getPetName());
        pet.setType(job.getPetType());
        pet.setOwner(job.getOwnerName());
        pet.setRegisteredAt(Instant.now().toString());
        pet.setStatus("ACTIVE");

        entityService.addItem("Pet", ENTITY_VERSION, pet).get();
        log.info("Created Pet {} from Job {}", pet.getId(), job.getId());

        // Create PetEvent entity for creation event
        PetEvent event = new PetEvent();
        String eventId = "event-" + petEventIdCounter.getAndIncrement();
        event.setEventId(eventId);
        event.setId(eventId);
        event.setPetId(pet.getId());
        event.setEventType("CREATED");
        event.setEventTimestamp(Instant.now().toString());
        event.setStatus("RECORDED");

        entityService.addItem("PetEvent", ENTITY_VERSION, event).get();
        log.info("Created PetEvent {} for Pet {}", event.getId(), pet.getId());

        // Update job status to COMPLETED (add new version)
        job.setStatus("COMPLETED");
        entityService.addItem("PetRegistrationJob", ENTITY_VERSION, job).get();
        log.info("PetRegistrationJob {} completed", job.getId());
    }

    // Business logic for Pet entity
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Example business logic: enrich pet data, trigger notifications, etc.
        log.info("Pet {} processed successfully", pet.getId());
    }

    // Business logic for PetEvent entity
    private void processPetEvent(PetEvent event) throws ExecutionException, InterruptedException {
        log.info("Processing PetEvent with ID: {}", event.getId());
        if (!event.isValid()) {
            log.error("Invalid PetEvent during processing: {}", event);
            event.setStatus("FAILED");
            entityService.addItem("PetEvent", ENTITY_VERSION, event).get();
            return;
        }
        // Example: Notify owner or trigger workflows here
        event.setStatus("PROCESSED");
        entityService.addItem("PetEvent", ENTITY_VERSION, event).get();
        log.info("PetEvent {} processed successfully", event.getId());
    }
}