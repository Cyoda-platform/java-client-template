package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetUpdateEvent;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // Keep petUpdateEventCache and counter locally as PetUpdateEvent has minor logic only
    private final Map<String, PetUpdateEvent> petUpdateEventCache = new HashMap<>();
    private final AtomicLong petUpdateEventIdCounter = new AtomicLong(1);

    private final AtomicLong petIdCounter = new AtomicLong(1); // for Pet id generation in update event

    // ------------------- PetJob Endpoints -------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws Exception {
        if (petJob == null || petJob.getJobId() == null || petJob.getJobId().isBlank()) {
            log.error("PetJob creation failed: Missing or blank jobId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("jobId is required");
        }

        petJob.setStatus("PENDING");
        petJob.setSubmittedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetJob",
                ENTITY_VERSION,
                petJob
        );
        UUID technicalId = idFuture.get();
        petJob.setId(technicalId.toString()); // set id from technicalId

        log.info("PetJob created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid PetJob id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob id format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "PetJob",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null) {
            log.error("PetJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }

        PetJob petJob = entityService.getObjectMapper().treeToValue(node, PetJob.class);
        petJob.setId(node.get("technicalId").asText());
        return ResponseEntity.ok(petJob);
    }

    // ------------------- Pet Endpoints -------------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws Exception {
        if (pet == null) {
            log.error("Pet creation failed: Request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: Missing or blank name");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
            log.error("Pet creation failed: Missing or blank species");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet species is required");
        }

        pet.setStatus("ACTIVE");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Pet",
                ENTITY_VERSION,
                pet
        );
        UUID technicalId = idFuture.get();
        pet.setId(technicalId.toString());

        log.info("Pet created with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Pet id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Pet",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null) {
            log.error("Pet not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        Pet pet = entityService.getObjectMapper().treeToValue(node, Pet.class);
        pet.setId(node.get("technicalId").asText());
        return ResponseEntity.ok(pet);
    }

    // ------------------- PetUpdateEvent Endpoints -------------------

    @PostMapping("/pets/update")
    public ResponseEntity<?> createPetUpdateEvent(@RequestBody PetUpdateEvent petUpdateEvent) {
        if (petUpdateEvent == null) {
            log.error("PetUpdateEvent creation failed: Request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetUpdateEvent data is required");
        }
        if (petUpdateEvent.getPetId() == null || petUpdateEvent.getPetId().isBlank()) {
            log.error("PetUpdateEvent creation failed: Missing or blank petId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required");
        }
        if (petUpdateEvent.getUpdatedFields() == null || petUpdateEvent.getUpdatedFields().isEmpty()) {
            log.error("PetUpdateEvent creation failed: updatedFields is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("updatedFields are required");
        }
        String id = "event-" + petUpdateEventIdCounter.getAndIncrement();
        petUpdateEvent.setId(id);
        petUpdateEvent.setStatus("PENDING");
        petUpdateEventCache.put(id, petUpdateEvent);

        log.info("PetUpdateEvent created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(petUpdateEvent);
    }

    @GetMapping("/pets/update/{id}")
    public ResponseEntity<?> getPetUpdateEvent(@PathVariable String id) {
        PetUpdateEvent petUpdateEvent = petUpdateEventCache.get(id);
        if (petUpdateEvent == null) {
            log.error("PetUpdateEvent not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateEvent not found");
        }
        return ResponseEntity.ok(petUpdateEvent);
    }
}