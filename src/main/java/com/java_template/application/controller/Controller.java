package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PurrfectPetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    private static final String PURRFECT_PET_JOB_ENTITY = "PurrfectPetJob";
    private static final String PET_ENTITY = "Pet";

    // --- PurrfectPetJob Endpoints ---

    @PostMapping("/purrfectPetJob")
    public CompletableFuture<ResponseEntity<?>> createPurrfectPetJob(@RequestBody PurrfectPetJob job) {
        if (job == null) {
            log.error("Received null PurrfectPetJob");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Job cannot be null"));
        }

        if (!job.isValid()) {
            log.error("Invalid PurrfectPetJob: {}", job);
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid job data"));
        }

        job.setStatus("PENDING");

        // add job via entityService
        return entityService.addItem(PURRFECT_PET_JOB_ENTITY, ENTITY_VERSION, job)
                .thenApply(technicalId -> {
                    job.setTechnicalId(technicalId);
                    job.setId("job-" + technicalId.toString());

                    log.info("Created PurrfectPetJob with technicalId: {}", technicalId);

                    return ResponseEntity.status(HttpStatus.CREATED).body(job);
                });
    }

    @GetMapping("/purrfectPetJob/{id}")
    public CompletableFuture<ResponseEntity<?>> getPurrfectPetJob(@PathVariable String id) {
        // id is expected to be "job-<UUID>", extract the UUID part
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id.replace("job-", ""));
        } catch (Exception e) {
            log.error("Invalid PurrfectPetJob id format: {}", id);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid job id"));
        }

        return entityService.getItem(PURRFECT_PET_JOB_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("PurrfectPetJob not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
                    }
                    // Map ObjectNode to PurrfectPetJob
                    PurrfectPetJob job = objectNode.traverse().readValueAs(PurrfectPetJob.class);
                    job.setTechnicalId(technicalId);
                    job.setId("job-" + technicalId.toString());
                    return ResponseEntity.ok(job);
                });
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Received null Pet");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Pet cannot be null"));
        }

        if (!pet.isValid()) {
            log.error("Invalid Pet data: {}", pet);
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid pet data"));
        }

        pet.setStatus("CREATED");

        return entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet)
                .thenApply(technicalId -> {
                    pet.setTechnicalId(technicalId);
                    pet.setId("pet-" + technicalId.toString());

                    log.info("Created Pet with technicalId: {}", technicalId);

                    return ResponseEntity.status(HttpStatus.CREATED).body(pet);
                });
    }

    @GetMapping("/pet/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id.replace("pet-", ""));
        } catch (Exception e) {
            log.error("Invalid Pet id format: {}", id);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet id"));
        }

        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("Pet not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
                    }
                    Pet pet = objectNode.traverse().readValueAs(Pet.class);
                    pet.setTechnicalId(technicalId);
                    pet.setId("pet-" + technicalId.toString());
                    return ResponseEntity.ok(pet);
                });
    }

    // --- PetEvent Endpoints (minor entity, keep local cache style) ---

    private final java.util.concurrent.ConcurrentHashMap<String, PetEvent> petEventCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong petEventIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null) {
            log.error("Received null PetEvent");
            return ResponseEntity.badRequest().body("PetEvent cannot be null");
        }

        petEvent.setId("event-" + petEventIdCounter.getAndIncrement());
        petEvent.setTechnicalId(UUID.randomUUID());

        if (!petEvent.isValid()) {
            log.error("Invalid PetEvent data: {}", petEvent);
            return ResponseEntity.badRequest().body("Invalid pet event data");
        }

        petEventCache.put(petEvent.getId(), petEvent);

        log.info("Created PetEvent with ID: {}", petEvent.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

}