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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final AtomicLong petIdCounter = new AtomicLong(1);
    private final AtomicLong petEventIdCounter = new AtomicLong(1);

    // POST /entity/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null) {
            logger.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }
        petJob.setStatus("PENDING");
        // id and technicalId will be assigned by entityService.addItem
        CompletableFuture<UUID> idFuture = entityService.addItem("PetJob", ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);
        String id = "job-" + technicalId.toString();
        petJob.setId(id);
        logger.info("Created PetJob with technicalId: {}", technicalId);

        try {
            processPetJob(petJob);
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
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        // We need to get by technicalId. The id passed is like "job-<UUID>" or "job-<number>", but we store technicalId as UUID.
        // Try to parse technicalId from id string after "job-"
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
        return ResponseEntity.ok(node);
    }

    // POST /entity/pet - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getAge() == null || pet.getAge() < 0) {
            logger.error("Invalid Pet creation request: missing or invalid fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid fields: name, species, age");
        }
        pet.setStatus("ACTIVE");
        // id and technicalId assigned by entityService
        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);
        String id = "pet-" + technicalId.toString();
        pet.setId(id);
        logger.info("Created Pet with technicalId: {}", technicalId);

        try {
            processPet(pet);
        } catch (Exception e) {
            logger.error("Error processing Pet with technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /entity/pet/{id} - get Pet by ID
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
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
        return ResponseEntity.ok(node);
    }

    // POST /entity/petevent - create PetEvent entity
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) throws ExecutionException, InterruptedException {
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
            processPetEvent(petEvent);
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
    public ResponseEntity<?> getPetEvent(@PathVariable String id) throws ExecutionException, InterruptedException {
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
        return ResponseEntity.ok(node);
    }

    private void processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        logger.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        String jobType = petJob.getJobType();
        if (jobType == null || jobType.isBlank()) {
            logger.error("PetJob jobType is missing or blank");
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob);
            throw new IllegalArgumentException("jobType is required");
        }

        Map<String, Object> payload = petJob.getPayload();
        if (payload == null || payload.isEmpty()) {
            logger.error("PetJob payload is missing or empty");
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob);
            throw new IllegalArgumentException("payload is required");
        }

        petJob.setStatus("PROCESSING");
        entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob);

        try {
            if ("AddPet".equalsIgnoreCase(jobType)) {
                String name = (String) payload.get("name");
                String species = (String) payload.get("species");
                Integer age = null;
                Object ageObj = payload.get("age");
                if (ageObj instanceof Integer) {
                    age = (Integer) ageObj;
                } else if (ageObj instanceof Number) {
                    age = ((Number) ageObj).intValue();
                }

                if (name == null || name.isBlank() || species == null || species.isBlank() || age == null || age < 0) {
                    logger.error("Invalid pet data in PetJob payload");
                    petJob.setStatus("FAILED");
                    entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                    throw new IllegalArgumentException("Invalid pet data in payload");
                }

                Pet pet = new Pet();
                pet.setName(name);
                pet.setSpecies(species);
                pet.setAge(age);
                pet.setStatus("ACTIVE");
                CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
                UUID petTechnicalId = petIdFuture.get();
                pet.setTechnicalId(petTechnicalId);
                pet.setId("pet-" + petTechnicalId.toString());
                logger.info("Created Pet with technicalId: {} via PetJob", petTechnicalId);

                PetEvent petEvent = new PetEvent();
                petEvent.setPetId(pet.getId());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(new Date());
                petEvent.setStatus("RECORDED");
                CompletableFuture<UUID> eventIdFuture = entityService.addItem("PetEvent", ENTITY_VERSION, petEvent);
                UUID eventTechnicalId = eventIdFuture.get();
                petEvent.setTechnicalId(eventTechnicalId);
                petEvent.setId("event-" + eventTechnicalId.toString());
                logger.info("Created PetEvent with technicalId: {} for Pet ID: {}", eventTechnicalId, pet.getId());

                processPetEvent(petEvent);

            } else if ("UpdatePetInfo".equalsIgnoreCase(jobType)) {
                logger.info("UpdatePetInfo jobType not implemented as this is prototype");
            } else {
                logger.error("Unsupported jobType in PetJob: {}", jobType);
                petJob.setStatus("FAILED");
                entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                throw new IllegalArgumentException("Unsupported jobType: " + jobType);
            }

            petJob.setStatus("COMPLETED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob);
            logger.info("PetJob with technicalId: {} completed successfully", petJob.getTechnicalId());
        } catch (Exception e) {
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob);
            logger.error("Exception during processing PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw e;
        }
    }

    private void processPetEvent(PetEvent petEvent) throws ExecutionException, InterruptedException {
        logger.info("Processing PetEvent with technicalId: {}", petEvent.getTechnicalId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            logger.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent);
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            logger.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent);
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            logger.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent);
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        petEvent.setStatus("PROCESSED");
        entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent);
        logger.info("PetEvent with technicalId: {} processed successfully", petEvent.getTechnicalId());
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        if (pet.getName() == null || pet.getName().isBlank() ||
                pet.getSpecies() == null || pet.getSpecies().isBlank() ||
                pet.getAge() == null || pet.getAge() < 0) {
            logger.error("Invalid Pet data");
            throw new IllegalArgumentException("Invalid Pet data");
        }
        logger.info("Pet with technicalId: {} is valid and active", pet.getTechnicalId());
    }
}