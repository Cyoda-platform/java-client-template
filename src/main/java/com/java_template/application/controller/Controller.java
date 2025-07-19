package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
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
    private static final String PURRFECT_PETS_JOB_MODEL = "PurrfectPetsJob";
    private static final String PET_MODEL = "Pet";

    // ----------------- PurrfectPetsJob Endpoints -----------------

    @PostMapping("/purrfectPetsJob")
    public CompletableFuture<ResponseEntity<?>> createPurrfectPetsJob(@Valid @RequestBody PurrfectPetsJob job) throws JsonProcessingException {
        if (job.getActionType() == null || job.getActionType().isBlank()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Missing or blank actionType"));
        }

        // clear business IDs and technicalId for new entry
        job.setId(null);
        job.setJobId(null);
        job.setStatus("PENDING");
        job.setCreatedAt(LocalDateTime.now());
        job.setTechnicalId(null);

        if (!job.isValid()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Job entity validation failed"));
        }

        return entityService.addItem(PURRFECT_PETS_JOB_MODEL, ENTITY_VERSION, job)
                .thenCompose(technicalId -> {
                    job.setTechnicalId(technicalId);
                    job.setStatus("PENDING");
                    return CompletableFuture.completedFuture(ResponseEntity.status(201).body(Map.of("jobId", technicalId.toString(), "status", job.getStatus())));
                });
    }

    @GetMapping("/purrfectPetsJob/{id}")
    public CompletableFuture<ResponseEntity<?>> getPurrfectPetsJob(@PathVariable String id) throws JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid UUID format for job ID"));
        }

        return entityService.getItem(PURRFECT_PETS_JOB_MODEL, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        return ResponseEntity.status(404).body("Job not found");
                    }
                    try {
                        PurrfectPetsJob job = objectMapper.treeToValue(objectNode, PurrfectPetsJob.class);
                        return ResponseEntity.ok(job);
                    } catch (JsonProcessingException e) {
                        log.error("Error converting ObjectNode to PurrfectPetsJob", e);
                        return ResponseEntity.status(500).body("Internal server error");
                    }
                });
    }

    // ----------------- Pet Endpoints -----------------

    @PostMapping("/pet")
    public CompletableFuture<ResponseEntity<?>> createPet(@Valid @RequestBody Pet pet) throws JsonProcessingException {
        if (pet.getName() == null || pet.getName().isBlank() ||
                pet.getType() == null || pet.getType().isBlank() ||
                pet.getBreed() == null || pet.getBreed().isBlank() ||
                pet.getAvailabilityStatus() == null || pet.getAvailabilityStatus().isBlank() ||
                pet.getStatus() == null || pet.getStatus().isBlank() ||
                pet.getAge() == null || pet.getAge() < 0) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Missing or invalid pet fields"));
        }

        pet.setId(null);
        pet.setPetId(null);
        pet.setTechnicalId(null);

        if (!pet.isValid()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Pet entity validation failed"));
        }

        return entityService.addItem(PET_MODEL, ENTITY_VERSION, pet)
                .thenCompose(technicalId -> {
                    pet.setTechnicalId(technicalId);
                    return CompletableFuture.completedFuture(ResponseEntity.status(201).body(Map.of("petId", technicalId.toString(), "status", pet.getStatus())));
                });
    }

    @GetMapping("/pet/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) throws JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid UUID format for pet ID"));
        }

        return entityService.getItem(PET_MODEL, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        return ResponseEntity.status(404).body("Pet not found");
                    }
                    try {
                        Pet pet = objectMapper.treeToValue(objectNode, Pet.class);
                        return ResponseEntity.ok(pet);
                    } catch (JsonProcessingException e) {
                        log.error("Error converting ObjectNode to Pet", e);
                        return ResponseEntity.status(500).body("Internal server error");
                    }
                });
    }
}