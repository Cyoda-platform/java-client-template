package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private final AtomicLong petRegistrationJobIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    @PostMapping("/petRegistrationJob")
    public ResponseEntity<?> createPetRegistrationJob(@RequestBody PetRegistrationJob job) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (job == null) {
            log.error("Received null PetRegistrationJob");
            return ResponseEntity.badRequest().body("PetRegistrationJob cannot be null");
        }
        String id = "job-" + petRegistrationJobIdCounter.getAndIncrement();
        job.setId(id);
        job.setJobId(id);
        job.setStatus("PENDING");

        if (!job.isValid()) {
            log.error("Invalid PetRegistrationJob: {}", job);
            return ResponseEntity.badRequest().body("Invalid PetRegistrationJob data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("PetRegistrationJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        log.info("Created PetRegistrationJob with technicalId: {}", technicalId);

        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("PetRegistrationJob", ENTITY_VERSION, technicalId);
        ObjectNode savedJobNode = jobFuture.get();
        PetRegistrationJob savedJob = objectMapper.treeToValue(savedJobNode, PetRegistrationJob.class);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedJob);
    }

    @GetMapping("/petRegistrationJob/{id}")
    public ResponseEntity<?> getPetRegistrationJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        PetRegistrationJob job = objectMapper.treeToValue(jobNode, PetRegistrationJob.class);
        return ResponseEntity.ok(job);
    }

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (pet == null) {
            log.error("Received null Pet");
            return ResponseEntity.badRequest().body("Pet cannot be null");
        }
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
        Pet savedPet = objectMapper.treeToValue(savedPetNode, Pet.class);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedPet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/pet")
    public ResponseEntity<?> listPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> petsFuture = entityService.getItems("Pet", ENTITY_VERSION);
        ArrayNode petsArray = petsFuture.get();
        return ResponseEntity.ok(petsArray);
    }

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent event) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (event == null) {
            log.error("Received null PetEvent");
            return ResponseEntity.badRequest().body("PetEvent cannot be null");
        }
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
        PetEvent savedEvent = objectMapper.treeToValue(savedEventNode, PetEvent.class);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedEvent);
    }

    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        PetEvent event = objectMapper.treeToValue(eventNode, PetEvent.class);
        return ResponseEntity.ok(event);
    }
}