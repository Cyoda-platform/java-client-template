package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
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

    private static final String JOB_ENTITY = "PurrfectPetsJob";
    private static final String PET_ENTITY = "Pet";

    // POST /controller/jobs - create new PurrfectPetsJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody PurrfectPetsJob job) throws ExecutionException, InterruptedException {
        if (job == null) {
            log.error("Received null job");
            return ResponseEntity.badRequest().body("Job cannot be null");
        }
        if (job.getJobId() == null || job.getJobId().isBlank()) {
            job.setJobId(UUID.randomUUID().toString());
        }
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (!job.isValid()) {
            log.error("Invalid job data: {}", job);
            return ResponseEntity.badRequest().body("Invalid job data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(JOB_ENTITY, ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);

        log.info("Created job with technicalId: {}", technicalId);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("status", job.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /controller/jobs/{id} - get job by id
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJobById(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(JOB_ENTITY, ENTITY_VERSION, id);
        ObjectNode jobNode = itemFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            log.error("Job not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(jobNode);
    }

    // POST /controller/pets - add new pet or pet state
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null) {
            log.error("Received null pet");
            return ResponseEntity.badRequest().body("Pet cannot be null");
        }
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            pet.setPetId(UUID.randomUUID().toString());
        }
        if (!pet.isValid()) {
            log.error("Invalid pet data: {}", pet);
            return ResponseEntity.badRequest().body("Invalid pet data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        log.info("Created pet with technicalId: {}", technicalId);

        Map<String, String> response = new HashMap<>();
        response.put("petId", pet.getPetId());
        response.put("status", pet.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /controller/pets/{id} - get pet by id
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPetById(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, id);
        ObjectNode petNode = itemFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(petNode);
    }

    // POST /controller/pets/{id}/update - create new pet version (immutable)
    @PostMapping("/pets/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable UUID id, @RequestBody Pet petUpdate) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> existingPetFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, id);
        ObjectNode existingPetNode = existingPetFuture.get();
        if (existingPetNode == null || existingPetNode.isEmpty()) {
            log.error("Pet not found for update with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        if (petUpdate == null) {
            log.error("Received null pet update");
            return ResponseEntity.badRequest().body("Pet update data cannot be null");
        }
        if (petUpdate.getPetId() == null || petUpdate.getPetId().isBlank()) {
            petUpdate.setPetId(UUID.randomUUID().toString());
        }
        if (!petUpdate.isValid()) {
            log.error("Invalid pet update data: {}", petUpdate);
            return ResponseEntity.badRequest().body("Invalid pet update data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, petUpdate);
        UUID technicalId = idFuture.get();
        petUpdate.setTechnicalId(technicalId);

        log.info("Created new pet version with technicalId: {}", technicalId);

        Map<String, String> response = new HashMap<>();
        response.put("petId", petUpdate.getPetId());
        response.put("status", petUpdate.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /controller/pets/{id}/deactivate - create deactivation record
    @PostMapping("/pets/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> existingPetFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, id);
        ObjectNode existingPetNode = existingPetFuture.get();
        if (existingPetNode == null || existingPetNode.isEmpty()) {
            log.error("Pet not found for deactivation with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        Pet deactivatedPet = new Pet();
        // Copy fields from existingPetNode to deactivatedPet
        deactivatedPet.setPetId(existingPetNode.get("petId").asText());
        deactivatedPet.setName(existingPetNode.get("name").asText(null));
        deactivatedPet.setSpecies(existingPetNode.get("species").asText(null));
        deactivatedPet.setBreed(existingPetNode.get("breed").asText(null));
        deactivatedPet.setAge(existingPetNode.hasNonNull("age") ? existingPetNode.get("age").asInt() : null);
        deactivatedPet.setStatus("DEACTIVATED");

        if (deactivatedPet.getPetId() == null || deactivatedPet.getPetId().isBlank()) {
            deactivatedPet.setPetId(UUID.randomUUID().toString());
        }
        if (!deactivatedPet.isValid()) {
            log.error("Invalid deactivated pet data for petId: {}", deactivatedPet.getPetId());
            return ResponseEntity.badRequest().body("Invalid deactivated pet data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, deactivatedPet);
        UUID technicalId = idFuture.get();
        deactivatedPet.setTechnicalId(technicalId);

        log.info("Deactivated pet with new technicalId: {}", technicalId);

        Map<String, String> response = new HashMap<>();
        response.put("petId", deactivatedPet.getPetId());
        response.put("status", deactivatedPet.getStatus());
        return ResponseEntity.ok(response);
    }

    // GET /controller/pets - list all pets
    @GetMapping("/pets")
    public ResponseEntity<?> listAllPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(PET_ENTITY, ENTITY_VERSION);
        ArrayNode petsArray = itemsFuture.get();
        return ResponseEntity.ok(petsArray);
    }
}