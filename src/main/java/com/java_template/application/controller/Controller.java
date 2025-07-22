package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // POST /controller/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException {
        if (petJob == null || petJob.getJobType() == null || petJob.getJobType().isBlank() || petJob.getPayload() == null) {
            log.error("Invalid PetJob creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: jobType and payload");
        }

        petJob.setTechnicalId(null); // clear any preset technicalId, will be set by entityService
        petJob.setStatus("PENDING");

        CompletableFuture<UUID> idFuture = entityService.addItem("PetJob", ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();

        petJob.setTechnicalId(technicalId);
        petJob.setStatus("PENDING");
        log.info("Created PetJob with technicalId: {}", technicalId);

        processPetJob(petJob);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", technicalId.toString(), "status", petJob.getStatus()));
    }

    // GET /controller/petjob/{id} - get PetJob by technicalId (UUID)
    @GetMapping("/petjob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetJob", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            log.error("PetJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = node.traverse().readValueAs(PetJob.class);
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
        pet.setTechnicalId(null);
        pet.setStatus("ACTIVE");

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);
        log.info("Created Pet with technicalId: {}", technicalId);

        processPet(pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{id} - get Pet by technicalId (UUID)
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = node.traverse().readValueAs(Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /controller/petevent - create PetEvent entity
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) throws ExecutionException, InterruptedException {
        if (petEvent == null || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
                || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
                || petEvent.getEventTimestamp() == null) {
            log.error("Invalid PetEvent creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: petId, eventType, eventTimestamp");
        }
        petEvent.setTechnicalId(null);
        petEvent.setStatus("RECORDED");

        CompletableFuture<UUID> idFuture = entityService.addItem("PetEvent", ENTITY_VERSION, petEvent);
        UUID technicalId = idFuture.get();
        petEvent.setTechnicalId(technicalId);
        log.info("Created PetEvent with technicalId: {}", technicalId);

        processPetEvent(petEvent);
        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /controller/petevent/{id} - get PetEvent by technicalId (UUID)
    @GetMapping("/petevent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetEvent", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            log.error("PetEvent not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        PetEvent petEvent = node.traverse().readValueAs(PetEvent.class);
        return ResponseEntity.ok(petEvent);
    }

    private void processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        log.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        String jobType = petJob.getJobType();
        if (jobType == null || jobType.isBlank()) {
            log.error("PetJob jobType is missing or blank");
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("jobType is required");
        }

        Map<String, Object> payload = petJob.getPayload();
        if (payload == null || payload.isEmpty()) {
            log.error("PetJob payload is missing or empty");
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("payload is required");
        }

        petJob.setStatus("PROCESSING");
        entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();

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
                    entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
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
                log.info("Created Pet with technicalId: {} via PetJob", petTechnicalId);

                PetEvent petEvent = new PetEvent();
                petEvent.setPetId(petTechnicalId.toString());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(new Date());
                petEvent.setStatus("RECORDED");

                CompletableFuture<UUID> eventIdFuture = entityService.addItem("PetEvent", ENTITY_VERSION, petEvent);
                UUID eventTechnicalId = eventIdFuture.get();
                petEvent.setTechnicalId(eventTechnicalId);
                log.info("Created PetEvent with technicalId: {} for Pet technicalId: {}", eventTechnicalId, petTechnicalId);

                processPetEvent(petEvent);

            } else if ("UpdatePetInfo".equalsIgnoreCase(jobType)) {
                log.info("UpdatePetInfo jobType not implemented as this is prototype");
            } else {
                log.error("Unsupported jobType in PetJob: {}", jobType);
                petJob.setStatus("FAILED");
                entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                throw new IllegalArgumentException("Unsupported jobType: " + jobType);
            }

            petJob.setStatus("COMPLETED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            log.info("PetJob with technicalId: {} completed successfully", petJob.getTechnicalId());

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            log.error("Exception during processing PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw e;
        }
    }

    private void processPetEvent(PetEvent petEvent) throws ExecutionException, InterruptedException {
        log.info("Processing PetEvent with technicalId: {}", petEvent.getTechnicalId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            log.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            log.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            log.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        petEvent.setStatus("PROCESSED");
        entityService.updateItem("PetEvent", ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
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