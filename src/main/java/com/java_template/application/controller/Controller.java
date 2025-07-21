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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String PETJOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";
    private static final String PETEVENT_ENTITY = "PetEvent";

    // POST /controller/petjob - create PetJob entity
    @PostMapping("/petjob")
    public ResponseEntity<?> createPetJob(@Valid @RequestBody PetJob petJob) throws ExecutionException, InterruptedException, JsonProcessingException {
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

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", technicalId.toString());
        response.put("status", petJob.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /controller/petjob/{technicalId} - get PetJob by technicalId
    @GetMapping("/petjob/{technicalId}")
    public ResponseEntity<?> getPetJob(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PETJOB_ENTITY, ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("PetJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = objectMapper.treeToValue(item, PetJob.class);
        return ResponseEntity.ok(petJob);
    }

    // POST /controller/pet - create new Pet version (immutable)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
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

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{technicalId} - get Pet by technicalId
    @GetMapping("/pet/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("Pet not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(item, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /controller/petevent - create PetEvent entity
    @PostMapping("/petevent")
    public ResponseEntity<?> createPetEvent(@Valid @RequestBody PetEvent petEvent) throws ExecutionException, InterruptedException, JsonProcessingException {
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

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    // GET /controller/petevent/{technicalId} - get PetEvent by technicalId
    @GetMapping("/petevent/{technicalId}")
    public ResponseEntity<?> getPetEvent(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PETEVENT_ENTITY, ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("PetEvent not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        PetEvent petEvent = objectMapper.treeToValue(item, PetEvent.class);
        return ResponseEntity.ok(petEvent);
    }

}