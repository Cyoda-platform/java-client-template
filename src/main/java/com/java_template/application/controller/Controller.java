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
}