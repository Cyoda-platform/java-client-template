package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetCreationJob;
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
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PET_CREATION_JOB_ENTITY = "PetCreationJob";
    private static final String PET_ENTITY = "Pet";

    // POST /entity/petCreationJob - create new PetCreationJob and trigger processing
    @PostMapping("/petCreationJob")
    public ResponseEntity<?> createPetCreationJob(@RequestBody PetCreationJob petCreationJob) throws ExecutionException, InterruptedException {
        if (petCreationJob == null || petCreationJob.getPetData() == null || petCreationJob.getPetData().isEmpty()) {
            log.error("Invalid petData in PetCreationJob creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid petData in request");
        }
        petCreationJob.setStatus("PENDING");
        CompletableFuture<UUID> idFuture = entityService.addItem(
                PET_CREATION_JOB_ENTITY,
                ENTITY_VERSION,
                petCreationJob
        );
        UUID technicalId = idFuture.get();
        petCreationJob.setTechnicalId(technicalId); // set technicalId for further reference

        log.info("PetCreationJob created with technicalId: {}", technicalId);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", technicalId.toString());
        response.put("status", petCreationJob.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /entity/petCreationJob/{id} - retrieve PetCreationJob status
    @GetMapping("/petCreationJob/{id}")
    public ResponseEntity<?> getPetCreationJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                PET_CREATION_JOB_ENTITY,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode jobNode = itemFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            log.error("PetCreationJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetCreationJob not found");
        }
        String status = jobNode.has("status") ? jobNode.get("status").asText() : null;
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", id);
        response.put("status", status);
        return ResponseEntity.ok(response);
    }

    // POST /entity/pet - create new Pet and trigger processing (for testing or direct pet creation)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Invalid pet data in Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data in request");
        }
        pet.setStatus("AVAILABLE");
        CompletableFuture<UUID> idFuture = entityService.addItem(
                PET_ENTITY,
                ENTITY_VERSION,
                pet
        );
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        log.info("Pet created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /entity/pet/{id} - retrieve Pet details
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                PET_ENTITY,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode petNode = itemFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        // Convert ObjectNode to Pet entity
        Pet pet = entityService.convertObjectNodeToEntity(petNode, Pet.class);
        pet.setTechnicalId(UUID.fromString(id));
        return ResponseEntity.ok(pet);
    }

    // POST /entity/pet/{id}/update - create new Pet version (avoided unless necessary)
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) throws ExecutionException, InterruptedException {
        UUID existingTechnicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> existingPetFuture = entityService.getItem(
                PET_ENTITY,
                ENTITY_VERSION,
                existingTechnicalId
        );
        ObjectNode existingPetNode = existingPetFuture.get();
        if (existingPetNode == null || existingPetNode.isEmpty()) {
            log.error("Pet not found for update with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        if (petUpdate == null || petUpdate.getName() == null || petUpdate.getName().isBlank() || petUpdate.getCategory() == null || petUpdate.getCategory().isBlank()) {
            log.error("Invalid pet data in update request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data in request");
        }
        // Create new Pet version with new technicalId
        petUpdate.setStatus(petUpdate.getStatus() != null ? petUpdate.getStatus() : "AVAILABLE");
        CompletableFuture<UUID> idFuture = entityService.addItem(
                PET_ENTITY,
                ENTITY_VERSION,
                petUpdate
        );
        UUID newTechnicalId = idFuture.get();
        petUpdate.setTechnicalId(newTechnicalId);

        log.info("Created new Pet version with technicalId: {}", newTechnicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(petUpdate);
    }

    // POST /entity/pet/{id}/deactivate - create deactivation record (avoided unless necessary)
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                PET_ENTITY,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found for deactivation with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = entityService.convertObjectNodeToEntity(petNode, Pet.class);
        pet.setStatus("DEACTIVATED");
        CompletableFuture<UUID> idFuture = entityService.addItem(
                PET_ENTITY,
                ENTITY_VERSION,
                pet
        );
        UUID newTechnicalId = idFuture.get();
        log.info("Pet deactivated with new technicalId: {}", newTechnicalId);
        return ResponseEntity.ok("Pet deactivation recorded");
    }
}