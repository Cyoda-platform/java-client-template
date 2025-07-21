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

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PETJOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";
    private static final String PETEVENT_ENTITY = "PetEvent";

    // POST /controller/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws Exception {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null) {
            log.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }

        petJob.setTechnicalId(UUID.randomUUID());
        petJob.setStatus("PENDING");

        CompletableFuture<UUID> idFuture = entityService.addItem(PETJOB_ENTITY, ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);

        log.info("Created PetJob with technicalId: {}", technicalId);

        try {
            processPetJob(petJob);
        } catch (Exception e) {
            log.error("Error processing PetJob with technicalId: {}", technicalId, e);
            petJob.setStatus("FAILED");
            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, technicalId, petJob).get();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", technicalId);
        response.put("status", petJob.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /controller/petjob/{technicalId} - get PetJob by technicalId
    @GetMapping("/petjob/{technicalId}")
    public ResponseEntity<?> getPetJob(@PathVariable UUID technicalId) throws Exception {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PETJOB_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("PetJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(item);
    }

    // POST /controller/pet - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws Exception {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getAge() == null || pet.getAge() < 0) {
            log.error("Invalid Pet creation request: missing or invalid fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid fields: name, species, age");
        }

        pet.setTechnicalId(UUID.randomUUID());
        pet.setStatus("ACTIVE");

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
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
    public ResponseEntity<?> getPet(@PathVariable UUID technicalId) throws Exception {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("Pet not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(item);
    }

    // POST /controller/petevent - create PetEvent entity
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) throws Exception {
        if (petEvent == null || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
                || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
                || petEvent.getEventTimestamp() == null) {
            log.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: petId, eventType, eventTimestamp");
        }

        petEvent.setTechnicalId(UUID.randomUUID());
        petEvent.setStatus("RECORDED");

        CompletableFuture<UUID> idFuture = entityService.addItem(PETEVENT_ENTITY, ENTITY_VERSION, petEvent);
        UUID technicalId = idFuture.get();
        petEvent.setTechnicalId(technicalId);

        log.info("Created PetEvent with technicalId: {}", technicalId);

        try {
            processPetEvent(petEvent);
        } catch (Exception e) {
            log.error("Error processing PetEvent with technicalId: {}", technicalId, e);
            petEvent.setStatus("FAILED");
            entityService.updateItem(PETEVENT_ENTITY, ENTITY_VERSION, technicalId, petEvent).get();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetEvent");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /controller/petevent/{technicalId} - get PetEvent by technicalId
    @GetMapping("/petevent/{technicalId}")
    public ResponseEntity<?> getPetEvent(@PathVariable UUID technicalId) throws Exception {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PETEVENT_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("PetEvent not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(item);
    }

    private void processPetJob(PetJob petJob) throws Exception {
        log.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        String jobType = petJob.getJobType();
        if (jobType == null || jobType.isBlank()) {
            log.error("PetJob jobType is missing or blank");
            petJob.setStatus("FAILED");
            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("jobType is required");
        }

        Map<String, Object> payload = petJob.getPayload();
        if (payload == null || payload.isEmpty()) {
            log.error("PetJob payload is missing or empty");
            petJob.setStatus("FAILED");
            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("payload is required");
        }

        petJob.setStatus("PROCESSING");
        entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();

        try {
            if ("AddPet".equalsIgnoreCase(jobType)) {
                // Extract pet info from payload
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
                    entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                    throw new IllegalArgumentException("Invalid pet data in payload");
                }

                // Create new Pet entity
                Pet pet = new Pet();
                pet.setTechnicalId(UUID.randomUUID());
                pet.setName(name);
                pet.setSpecies(species);
                pet.setAge(age);
                pet.setStatus("ACTIVE");

                UUID petTechnicalId = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet).get();
                pet.setTechnicalId(petTechnicalId);

                log.info("Created Pet with technicalId: {} via PetJob", petTechnicalId);

                // Create PetEvent for created pet
                PetEvent petEvent = new PetEvent();
                petEvent.setTechnicalId(UUID.randomUUID());
                petEvent.setPetId(petTechnicalId.toString());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(new Date());
                petEvent.setStatus("RECORDED");

                UUID petEventTechnicalId = entityService.addItem(PETEVENT_ENTITY, ENTITY_VERSION, petEvent).get();
                petEvent.setTechnicalId(petEventTechnicalId);

                log.info("Created PetEvent with technicalId: {} for Pet technicalId: {}", petEventTechnicalId, petTechnicalId);

                processPetEvent(petEvent);

            } else if ("UpdatePetInfo".equalsIgnoreCase(jobType)) {
                log.info("UpdatePetInfo jobType not implemented as this is prototype");
            } else {
                log.error("Unsupported jobType in PetJob: {}", jobType);
                petJob.setStatus("FAILED");
                entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                throw new IllegalArgumentException("Unsupported jobType: " + jobType);
            }

            petJob.setStatus("COMPLETED");
            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            log.info("PetJob with technicalId: {} completed successfully", petJob.getTechnicalId());

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            log.error("Exception during processing PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw e;
        }
    }

    private void processPetEvent(PetEvent petEvent) throws Exception {
        log.info("Processing PetEvent with technicalId: {}", petEvent.getTechnicalId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            log.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem(PETEVENT_ENTITY, ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            log.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem(PETEVENT_ENTITY, ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            log.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            entityService.updateItem(PETEVENT_ENTITY, ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        // Example business logic: here could be workflow triggers, notifications, etc.
        // For prototype, just mark as processed
        petEvent.setStatus("PROCESSED");
        entityService.updateItem(PETEVENT_ENTITY, ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
        log.info("PetEvent with technicalId: {} processed successfully", petEvent.getTechnicalId());
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
}