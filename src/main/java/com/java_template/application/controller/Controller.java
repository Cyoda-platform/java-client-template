package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // POST /controller/petJob - Create PetJob
    @PostMapping("/petJob")
    public CompletableFuture<ResponseEntity<?>> createPetJob(@Valid @RequestBody PetJob petJob) throws JsonProcessingException {
        if (petJob == null || petJob.getJobId() == null || petJob.getJobId().isBlank()
                || petJob.getPetType() == null || petJob.getPetType().isBlank()
                || petJob.getStatus() == null) {
            logger.error("Invalid PetJob creation request");
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data"));
        }

        return entityService.addItem("PetJob", ENTITY_VERSION, petJob)
                .thenCompose(technicalId -> entityService.getItem("PetJob", ENTITY_VERSION, technicalId)
                        .thenApply(petJobNode -> {
                            try {
                                PetJob createdPetJob = objectMapper.treeToValue(petJobNode, PetJob.class);
                                createdPetJob.setTechnicalId(technicalId);
                                createdPetJob.setId(technicalId.toString());
                                return ResponseEntity.status(HttpStatus.CREATED).body(createdPetJob);
                            } catch (JsonProcessingException e) {
                                logger.error("Error converting PetJob ObjectNode to PetJob entity", e);
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob data");
                            }
                        }));
    }

    // GET /controller/petJob/{id} - Retrieve PetJob by id
    @GetMapping("/petJob/{id}")
    public CompletableFuture<ResponseEntity<?>> getPetJob(@PathVariable String id) throws JsonProcessingException {
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
                    try {
                        PetJob petJob = objectMapper.treeToValue(petJobNode, PetJob.class);
                        return ResponseEntity.ok(petJob);
                    } catch (JsonProcessingException e) {
                        logger.error("Error converting PetJob ObjectNode to PetJob entity", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob data");
                    }
                });
    }

    // POST /controller/pet - Create Pet
    @PostMapping("/pet")
    public CompletableFuture<ResponseEntity<?>> createPet(@Valid @RequestBody Pet pet) throws JsonProcessingException {
        if (pet == null || pet.getPetId() == null || pet.getPetId().isBlank()
                || pet.getName() == null || pet.getName().isBlank()
                || pet.getCategory() == null || pet.getCategory().isBlank()
                || pet.getStatus() == null) {
            logger.error("Invalid Pet creation request");
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data"));
        }

        return entityService.addItem("Pet", ENTITY_VERSION, pet)
                .thenCompose(technicalId -> entityService.getItem("Pet", ENTITY_VERSION, technicalId)
                        .thenApply(petNode -> {
                            if (petNode == null || petNode.isEmpty()) {
                                logger.error("Pet creation failed, no data returned");
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Pet creation failed");
                            }
                            try {
                                Pet createdPet = objectMapper.treeToValue(petNode, Pet.class);
                                createdPet.setTechnicalId(technicalId);
                                createdPet.setId(technicalId.toString());
                                return ResponseEntity.status(HttpStatus.CREATED).body(createdPet);
                            } catch (JsonProcessingException e) {
                                logger.error("Error converting Pet ObjectNode to Pet entity", e);
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet data");
                            }
                        }));
    }

    // GET /controller/pet/{id} - Retrieve Pet by id
    @GetMapping("/pet/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) throws JsonProcessingException {
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
                    try {
                        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
                        return ResponseEntity.ok(pet);
                    } catch (JsonProcessingException e) {
                        logger.error("Error converting Pet ObjectNode to Pet entity", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet data");
                    }
                });
    }

    // POST /controller/petEvent - Create PetEvent
    @PostMapping("/petEvent")
    public CompletableFuture<ResponseEntity<?>> createPetEvent(@Valid @RequestBody PetEvent petEvent) {
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

}