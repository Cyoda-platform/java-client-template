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

                    return processPurrfectPetsJob(job)
                            .thenApply(v -> ResponseEntity.status(201).body(Map.of("jobId", technicalId.toString(), "status", job.getStatus())));
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
                    return processPet(pet).thenApply(v -> ResponseEntity.status(201).body(Map.of("petId", technicalId.toString(), "status", pet.getStatus())));
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

    // ----------------- Process Methods -----------------

    private CompletableFuture<Void> processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());

        // Validation of actionType
        String action = job.getActionType().toUpperCase(Locale.ROOT).trim();
        if (!action.equals("FETCH_PETS") && !action.equals("UPDATE_PET_STATUS")) {
            log.error("Invalid actionType: {}", job.getActionType());
            job.setStatus("FAILED");
            // Update job status in external service by creating a new version
            return entityService.addItem(PURRFECT_PETS_JOB_MODEL, ENTITY_VERSION, job)
                    .thenAccept(ignored -> {});
        }

        job.setStatus("PROCESSING");

        if (action.equals("FETCH_PETS")) {
            // Simulate fetching pets from Petstore API and creating Pet entities
            // Here we simulate with dummy data for prototype

            Pet samplePet1 = new Pet();
            samplePet1.setName("Simba");
            samplePet1.setType("cat");
            samplePet1.setBreed("Siamese");
            samplePet1.setAge(3);
            samplePet1.setAvailabilityStatus("AVAILABLE");
            samplePet1.setStatus("NEW");

            Pet samplePet2 = new Pet();
            samplePet2.setName("Buddy");
            samplePet2.setType("dog");
            samplePet2.setBreed("Beagle");
            samplePet2.setAge(5);
            samplePet2.setAvailabilityStatus("AVAILABLE");
            samplePet2.setStatus("NEW");

            List<Pet> fetchedPets = List.of(samplePet1, samplePet2);

            // Validate pets and add them via entity service
            List<Pet> validPets = new ArrayList<>();
            for (Pet pet : fetchedPets) {
                if (pet.isValid()) {
                    validPets.add(pet);
                } else {
                    log.error("Invalid pet data during fetch: {}", pet);
                }
            }

            return entityService.addItems(PET_MODEL, ENTITY_VERSION, validPets)
                    .thenCompose(petTechnicalIds -> {
                        CompletableFuture<Void> allProcesses = CompletableFuture.allOf(
                                petTechnicalIds.stream().map(id -> {
                                    // Retrieve pet by id to get full object and process - but we have pet objects locally
                                    // Since we have the pets locally, assign technicalId and process them
                                    int idx = petTechnicalIds.indexOf(id);
                                    Pet pet = validPets.get(idx);
                                    pet.setTechnicalId(id);
                                    return processPet(pet);
                                }).toArray(CompletableFuture[]::new)
                        );
                        return allProcesses.thenCompose(v -> {
                            job.setStatus("COMPLETED");
                            return entityService.addItem(PURRFECT_PETS_JOB_MODEL, ENTITY_VERSION, job).thenAccept(ignored -> {});
                        });
                    })
                    .exceptionally(e -> {
                        log.error("Error processing pets fetch", e);
                        job.setStatus("FAILED");
                        entityService.addItem(PURRFECT_PETS_JOB_MODEL, ENTITY_VERSION, job);
                        return null;
                    });
        } else if (action.equals("UPDATE_PET_STATUS")) {
            // For prototype, no specific update logic implemented
            log.info("Update pet status action requested, no implementation in prototype");
            job.setStatus("COMPLETED");
            return entityService.addItem(PURRFECT_PETS_JOB_MODEL, ENTITY_VERSION, job).thenAccept(ignored -> {});
        }

        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (!pet.isValid()) {
            log.error("Validation failed for Pet technicalId: {}", pet.getTechnicalId());
            return CompletableFuture.completedFuture(null);
        }

        if ("NEW".equalsIgnoreCase(pet.getStatus())) {
            pet.setStatus("ACTIVE");
            // Create new version with updated status
            return entityService.addItem(PET_MODEL, ENTITY_VERSION, pet)
                    .thenAccept(id -> log.info("Pet status updated to ACTIVE for technicalId: {}", id));
        }

        // Further processing could involve notifications, indexing, etc.
        return CompletableFuture.completedFuture(null);
    }
}