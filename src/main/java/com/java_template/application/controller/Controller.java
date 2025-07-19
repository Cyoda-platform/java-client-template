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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // -------- PurrfectPetsJob Endpoints --------

    @PostMapping("/jobs")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) throws ExecutionException, InterruptedException {
        if (job == null) {
            logger.error("Received null job request");
            return ResponseEntity.badRequest().body("Job data must be provided");
        }
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (!job.isValid()) {
            logger.error("Invalid job data");
            return ResponseEntity.badRequest().body("Invalid job data");
        }
        // Add job to external entityService
        CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();

        logger.info("Created PurrfectPetsJob with technicalId: {}", technicalId);

        // After creation, retrieve the stored job to get technicalId and set id/jobId fields for processing
        CompletableFuture<ObjectNode> jobNodeFuture = entityService.getItem("PurrfectPetsJob", ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobNodeFuture.get();

        // Map ObjectNode to PurrfectPetsJob for processing
        // But since technicalId is not in entity, set id and jobId accordingly
        // We will just pass the original job with id fields set to technicalId string
        String techIdStr = technicalId.toString();
        job.setId(techIdStr);
        job.setJobId(techIdStr);

        return ResponseEntity.status(201).body(Map.of("jobId", techIdStr, "status", job.getStatus()));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for job id {}", id);
            return ResponseEntity.badRequest().body("Invalid job id format");
        }
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("PurrfectPetsJob", ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            logger.error("PurrfectPetsJob not found for id {}", id);
            return ResponseEntity.status(404).body("Job not found");
        }
        return ResponseEntity.ok(jobNode);
    }

    // -------- Pet Endpoints --------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null) {
            logger.error("Received null pet request");
            return ResponseEntity.badRequest().body("Pet data must be provided");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("NEW");
        }
        if (!pet.isValid()) {
            logger.error("Invalid pet data");
            return ResponseEntity.badRequest().body("Invalid pet data");
        }
        // Add pet to external entityService
        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();

        logger.info("Created Pet with technicalId: {}", technicalId);

        // Retrieve created pet entity to pass to processPet
        CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = petNodeFuture.get();

        // Map ObjectNode to Pet object for processing is complicated, so just set id fields on original pet
        String techIdStr = technicalId.toString();
        pet.setId(techIdStr);
        pet.setPetId(techIdStr);

        return ResponseEntity.status(201).body(Map.of("petId", techIdStr, "status", pet.getStatus()));
    }

    @GetMapping("/pets")
    public ResponseEntity<?> getAllPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> petsFuture = entityService.getItems("Pet", ENTITY_VERSION);
        ArrayNode petsArray = petsFuture.get();
        return ResponseEntity.ok(petsArray);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPetById(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for pet id {}", id);
            return ResponseEntity.badRequest().body("Invalid pet id format");
        }
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            logger.error("Pet not found for id {}", id);
            return ResponseEntity.status(404).body("Pet not found");
        }
        return ResponseEntity.ok(petNode);
    }
}