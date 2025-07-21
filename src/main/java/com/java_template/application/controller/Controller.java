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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String ENTITY_MODEL_PETJOB = "PetJob";
    private static final String ENTITY_MODEL_PET = "Pet";
    // PetEvent kept local cache as minor entity, no refactoring for it as per instructions
    
    private final Map<String, PetEvent> petEventCache = new HashMap<>();
    private long petEventIdCounter = 1;

    // POST /controller/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null) {
            log.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }
        petJob.setStatus("PENDING");
        UUID technicalId = entityService.addItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob).get();
        petJob.setTechnicalId(technicalId);
        log.info("Created PetJob with technicalId: {}", technicalId);

        try {
            processPetJob(petJob);
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
    public ResponseEntity<?> getPetJob(@PathVariable UUID technicalId) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> future = entityService.getItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, technicalId);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("PetJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = JsonUtils.convertObjectNodeTo(petJobClass(), node);
        return ResponseEntity.ok(petJob);
    }

    // POST /controller/pet - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
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
            processPet(pet);
        } catch (Exception e) {
            log.error("Error processing Pet with technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{technicalId} - get Pet by technicalId
    @GetMapping("/pet/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable UUID technicalId) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> future = entityService.getItem(ENTITY_MODEL_PET, ENTITY_VERSION, technicalId);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("Pet not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = JsonUtils.convertObjectNodeTo(petClass(), node);
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
            processPetEvent(petEvent);
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

    private void processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        log.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        String jobType = petJob.getJobType();
        if (jobType == null || jobType.isBlank()) {
            log.error("PetJob jobType is missing or blank");
            petJob.setStatus("FAILED");
            entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("jobType is required");
        }

        Map<String, Object> payload = petJob.getPayload();
        if (payload == null || payload.isEmpty()) {
            log.error("PetJob payload is missing or empty");
            petJob.setStatus("FAILED");
            entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("payload is required");
        }

        petJob.setStatus("PROCESSING");
        entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();

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
                    log.error("Invalid pet data in PetJob payload");
                    petJob.setStatus("FAILED");
                    entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                    throw new IllegalArgumentException("Invalid pet data in payload");
                }

                Pet pet = new Pet();
                pet.setName(name);
                pet.setSpecies(species);
                pet.setAge(age);
                pet.setStatus("ACTIVE");
                UUID petTechnicalId = entityService.addItem(ENTITY_MODEL_PET, ENTITY_VERSION, pet).get();
                pet.setTechnicalId(petTechnicalId);
                log.info("Created Pet with technicalId: {} via PetJob", petTechnicalId);

                PetEvent petEvent = new PetEvent();
                String eventId = "event-" + petEventIdCounter++;
                petEvent.setId(eventId);
                petEvent.setTechnicalId(UUID.randomUUID());
                petEvent.setPetId(petTechnicalId.toString());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(new Date());
                petEvent.setStatus("RECORDED");
                petEventCache.put(eventId, petEvent);
                log.info("Created PetEvent with ID: {} for Pet technicalId: {}", eventId, petTechnicalId);

                processPetEvent(petEvent);

            } else if ("UpdatePetInfo".equalsIgnoreCase(jobType)) {
                log.info("UpdatePetInfo jobType not implemented as this is prototype");
            } else {
                log.error("Unsupported jobType in PetJob: {}", jobType);
                petJob.setStatus("FAILED");
                entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                throw new IllegalArgumentException("Unsupported jobType: " + jobType);
            }

            petJob.setStatus("COMPLETED");
            entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            log.info("PetJob with technicalId: {} completed successfully", petJob.getTechnicalId());

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            entityService.updateItem(ENTITY_MODEL_PETJOB, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            log.error("Exception during processing PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw e;
        }
    }

    private void processPetEvent(PetEvent petEvent) {
        log.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            log.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            log.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            log.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        petEvent.setStatus("PROCESSED");
        petEventCache.put(petEvent.getId(), petEvent);
        log.info("PetEvent with ID: {} processed successfully", petEvent.getId());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        if (pet.getName() == null || pet.getName().isBlank() ||
            pet.getSpecies() == null || pet.getSpecies().isBlank() ||
            pet.getAge() == null || pet.getAge() < 0) {
            log.error("Invalid Pet data");
            throw new IllegalArgumentException("Invalid Pet data");
        }
        log.info("Pet with technicalId: {} is valid and active", pet.getTechnicalId());
    }

    // Utility methods for JSON conversion (simulated) - you can implement these or use Jackson ObjectMapper
    private static Class<PetJob> petJobClass() {
        return PetJob.class;
    }
    private static Class<Pet> petClass() {
        return Pet.class;
    }

    private static class JsonUtils {
        static <T> T convertObjectNodeTo(Class<T> clazz) {
            throw new UnsupportedOperationException("Implement JSON conversion");
        }
        static <T> T convertObjectNodeTo(Class<T> clazz, ObjectNode node) {
            // Implement JSON deserialization from ObjectNode to T (PetJob or Pet)
            throw new UnsupportedOperationException("Implement JSON conversion");
        }
    }
}