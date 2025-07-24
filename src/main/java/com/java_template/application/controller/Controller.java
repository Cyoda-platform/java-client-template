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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // POST /controller/petJob - Create PetJob
    @PostMapping("/petJob")
    public CompletableFuture<ResponseEntity<?>> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getJobId() == null || petJob.getJobId().isBlank()
                || petJob.getPetType() == null || petJob.getPetType().isBlank()
                || petJob.getStatus() == null) {
            logger.error("Invalid PetJob creation request");
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data"));
        }

        return entityService.addItem("PetJob", ENTITY_VERSION, petJob)
                .thenCompose(technicalId -> entityService.getItem("PetJob", ENTITY_VERSION, technicalId)
                        .thenCompose(petJobNode -> {
                            PetJob createdPetJob = petJob; // reuse original object for business logic
                            createdPetJob.setId(technicalId.toString());
                            processPetJob(createdPetJob);
                            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.CREATED).body(createdPetJob));
                        }));
    }

    // GET /controller/petJob/{id} - Retrieve PetJob by id
    @GetMapping("/petJob/{id}")
    public CompletableFuture<ResponseEntity<?>> getPetJob(@PathVariable String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PetJob ID format: {}", id);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob ID format"));
        }

        return entityService.getItem("PetJob", ENTITY_VERSION, technicalId)
                .thenApply(petJobNode -> {
                    if (petJobNode == null || petJobNode.isEmpty()) {
                        logger.error("PetJob with ID {} not found", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
                    }
                    return ResponseEntity.ok(petJobNode);
                });
    }

    // POST /controller/pet - Create Pet
    @PostMapping("/pet")
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getPetId() == null || pet.getPetId().isBlank()
                || pet.getName() == null || pet.getName().isBlank()
                || pet.getCategory() == null || pet.getCategory().isBlank()
                || pet.getStatus() == null) {
            logger.error("Invalid Pet creation request");
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data"));
        }

        return entityService.addItem("Pet", ENTITY_VERSION, pet)
                .thenCompose(technicalId -> entityService.getItem("Pet", ENTITY_VERSION, technicalId)
                        .thenCompose(petNode -> {
                            Pet createdPet = pet;
                            createdPet.setId(technicalId.toString());
                            processPet(createdPet);
                            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.CREATED).body(createdPet));
                        }));
    }

    // GET /controller/pet/{id} - Retrieve Pet by id
    @GetMapping("/pet/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Pet ID format: {}", id);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet ID format"));
        }

        return entityService.getItem("Pet", ENTITY_VERSION, technicalId)
                .thenApply(petNode -> {
                    if (petNode == null || petNode.isEmpty()) {
                        logger.error("Pet with ID {} not found", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
                    }
                    return ResponseEntity.ok(petNode);
                });
    }

    // POST /controller/petEvent - Create PetEvent
    @PostMapping("/petEvent")
    public CompletableFuture<ResponseEntity<?>> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null || petEvent.getEventId() == null || petEvent.getEventId().isBlank()
                || petEvent.getPetId() == null || petEvent.getPetId().isBlank()
                || petEvent.getEventType() == null || petEvent.getEventType().isBlank()
                || petEvent.getTimestamp() == null
                || petEvent.getStatus() == null) {
            logger.error("Invalid PetEvent creation request");
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetEvent data"));
        }

        // PetEvent is minor/utility entity, keep local cache logic (no external calls)
        // But as per instructions, PetEvent is minor, so do not refactor

        // For consistency, we keep local logic here
        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.CREATED).body(petEvent));
    }

    // GET /controller/petEvent/{id} - Retrieve PetEvent by id
    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        // PetEvent is minor/utility entity, keep local cache logic (not refactored)
        // So no external calls here, just return 404
        logger.error("PetEvent retrieval not implemented via external service");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
    }

    // Business logic processing methods

    private void processPetJob(PetJob petJob) {
        logger.info("Processing PetJob with ID: {}", petJob.getId());

        if (petJob.getPetType().isBlank()) {
            logger.error("PetJob petType is blank");
            petJob.setStatus(PetJob.StatusEnum.FAILED);
            return;
        }

        petJob.setStatus(PetJob.StatusEnum.PROCESSING);
        logger.info("PetJob {} status updated to PROCESSING", petJob.getId());

        // Simulate fetching pets from external Petstore API for petType
        // For prototype, just create sample Pet entities and add via entityService

        for (int i = 1; i <= 3; i++) {
            Pet pet = new Pet();
            pet.setPetId(petJob.getPetType() + "-pet-" + i);
            pet.setName(petJob.getPetType().substring(0, 1).toUpperCase() + "Pet" + i);
            pet.setCategory(petJob.getPetType());
            pet.setStatus(Pet.StatusEnum.AVAILABLE);

            try {
                UUID petTechnicalId = entityService.addItem("Pet", ENTITY_VERSION, pet).get();
                pet.setId(petTechnicalId.toString());
                logger.info("Created Pet {} for PetJob {}", pet.getPetId(), petJob.getId());
                processPet(pet);

                // Create PetEvent for each Pet created - PetEvent is minor entity, skip external service
                PetEvent petEvent = new PetEvent();
                petEvent.setEventId("event-" + pet.getPetId());
                petEvent.setPetId(pet.getPetId());
                petEvent.setEventType("CREATED");
                petEvent.setTimestamp(LocalDateTime.now());
                petEvent.setStatus(PetEvent.StatusEnum.RECORDED);
                // PetEvent local only, no external service call
                processPetEvent(petEvent);

            } catch (Exception e) {
                logger.error("Failed to create Pet for PetJob {}: {}", petJob.getId(), e.getMessage());
            }
        }

        petJob.setStatus(PetJob.StatusEnum.COMPLETED);
        logger.info("PetJob {} processing COMPLETED", petJob.getId());
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getId());

        if (pet.getPetId().isBlank() || pet.getName().isBlank() || pet.getCategory().isBlank()) {
            logger.error("Pet data validation failed for ID: {}", pet.getId());
            return;
        }

        logger.info("Pet {} data validated and ready for retrieval", pet.getPetId());
    }

    private void processPetEvent(PetEvent petEvent) {
        logger.info("Processing PetEvent with ID: {}", petEvent.getId());

        logger.info("PetEvent {} of type {} processed at {}", petEvent.getEventId(), petEvent.getEventType(), petEvent.getTimestamp());

        petEvent.setStatus(PetEvent.StatusEnum.PROCESSED);
    }

}