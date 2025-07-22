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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    private final AtomicLong petIdCounter = new AtomicLong(1);
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // POST /entity/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null || petJob.getPayload().isBlank()) {
            logger.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }
        petJob.setStatus("PENDING");
        CompletableFuture<UUID> idFuture = entityService.addItem("PetJob", ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);
        String id = "job-" + technicalId.toString();
        petJob.setId(id);
        logger.info("Created PetJob with technicalId: {}", technicalId);

        try {
            // Process method removed
        } catch (Exception e) {
            logger.error("Error processing PetJob with technicalId: {}", technicalId, e);
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", ENTITY_VERSION, technicalId, petJob);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", id, "status", petJob.getStatus()));
    }

    // GET /entity/petjob/{id} - get PetJob by ID (id here is the string id like "job-...")
    @GetMapping("/petjob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        String[] parts = id.split("-", 2);
        if (parts.length < 2) {
            logger.error("Invalid PetJob id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob id format");
        }
        UUID technicalId;
        try {
            technicalId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PetJob technicalId: {}", parts[1]);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob technicalId");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetJob", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.error("PetJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = objectMapper.treeToValue(node, PetJob.class);
        return ResponseEntity.ok(petJob);
    }

    // POST /entity/pet - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getAge() == null || pet.getAge() < 0) {
            logger.error("Invalid Pet creation request: missing or invalid fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid fields: name, species, age");
        }
        pet.setStatus("ACTIVE");
        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);
        String id = "pet-" + technicalId.toString();
        pet.setId(id);
        logger.info("Created Pet with technicalId: {}", technicalId);

        try {
            // Process method removed
        } catch (Exception e) {
            logger.error("Error processing Pet with technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /entity/pet/{id} - get Pet by ID
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        String[] parts = id.split("-", 2);
        if (parts.length < 2) {
            logger.error("Invalid Pet id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format");
        }
        UUID technicalId;
        try {
            technicalId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Pet technicalId: {}", parts[1]);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet technicalId");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.error("Pet not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /entity/petevent - create PetEvent entity
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (petEvent == null || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
                || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
                || petEvent.getEventTimestamp() == null) {
            logger.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: petId, eventType, eventTimestamp");
        }
        petEvent.setStatus("RECORDED");
        CompletableFuture<UUID> idFuture = entityService.addItem("PetEvent", ENTITY_VERSION, petEvent);
        UUID technicalId = idFuture.get();
        petEvent.setTechnicalId(technicalId);
        String id = "event-" + technicalId.toString();
        petEvent.setId(id);
        logger.info("Created PetEvent with technicalId: {}", technicalId);

        try {
            // Process method removed
        } catch (Exception e) {
            logger.error("Error processing PetEvent with technicalId: {}", technicalId, e);
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", ENTITY_VERSION, technicalId, petEvent);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetEvent");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /entity/petevent/{id} - get PetEvent by ID
    @GetMapping("/petevent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        String[] parts = id.split("-", 2);
        if (parts.length < 2) {
            logger.error("Invalid PetEvent id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetEvent id format");
        }
        UUID technicalId;
        try {
            technicalId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PetEvent technicalId: {}", parts[1]);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetEvent technicalId");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetEvent", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.error("PetEvent not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        PetEvent petEvent = objectMapper.treeToValue(node, PetEvent.class);
        return ResponseEntity.ok(petEvent);
    }
}