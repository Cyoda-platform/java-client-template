package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private static final String PURRFECT_PETS_JOB_MODEL = "PurrfectPetsJob";
    private static final String PET_MODEL = "Pet";

    // ----------------- PurrfectPetsJob Endpoints -----------------

    @PostMapping("/purrfectPetsJob")
    public CompletableFuture<ResponseEntity<?>> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        // Basic validation of required fields
        if (job.getActionType() == null || job.getActionType().isBlank()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Missing or blank actionType"));
        }

        // Set fields: id and jobId will be replaced by external service's technicalId usage internally
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
                    // Set technicalId and other fields for processing
                    job.setTechnicalId(technicalId);
                    job.setStatus("PENDING");

                    return CompletableFuture.completedFuture(ResponseEntity.status(201).body(Map.of("jobId", technicalId.toString(), "status", job.getStatus())));
                });
    }

    @GetMapping("/purrfectPetsJob/{id}")
    public CompletableFuture<ResponseEntity<?>> getPurrfectPetsJob(@PathVariable String id) {
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
                    return ResponseEntity.ok(objectNode);
                });
    }

    // ----------------- Pet Endpoints -----------------

    @PostMapping("/pet")
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody Pet pet) {
        // Validate required fields (partial check)
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
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) {
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
                    return ResponseEntity.ok(objectNode);
                });
    }
}