package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String PET_JOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";

    // POST /controller/petJob - Create PetJob entity
    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (petJob == null || petJob.getType() == null || petJob.getType().isBlank()) {
            log.error("Invalid PetJob: missing type");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: type");
        }
        petJob.setStatus(PetJob.StatusEnum.PENDING);

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_JOB_ENTITY, ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);

        log.info("Created PetJob with technicalId: {}", technicalId);

        // After processing, update PetJob status by creating a new version (create new entity)
        CompletableFuture<UUID> updatedIdFuture = entityService.addItem(PET_JOB_ENTITY, ENTITY_VERSION, petJob);
        UUID updatedTechnicalId = updatedIdFuture.get();
        petJob.setTechnicalId(updatedTechnicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", updatedTechnicalId.toString(), "status", petJob.getStatus().name()));
    }

    // GET /controller/petJob/{id} - Retrieve PetJob by technicalId
    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for PetJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_JOB_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode result = itemFuture.get();
        if (result == null || result.isEmpty()) {
            log.error("PetJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = objectMapper.treeToValue(result, PetJob.class);
        return ResponseEntity.ok(petJob);
    }

    // POST /controller/pet - Create Pet entity
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Invalid Pet: missing name or category");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: name and category");
        }
        if (pet.getStatus() == null) {
            pet.setStatus(Pet.StatusEnum.AVAILABLE);
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        log.info("Created Pet with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{id} - Retrieve Pet by technicalId
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode result = itemFuture.get();
        if (result == null || result.isEmpty()) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(result, Pet.class);
        return ResponseEntity.ok(pet);
    }

}