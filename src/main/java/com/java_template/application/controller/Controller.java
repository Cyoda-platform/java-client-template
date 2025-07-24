package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.PetStatusUpdate;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // POST /entity/petIngestionJob - create PetIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (job.getSource() == null || job.getSource().isBlank()) {
            logger.error("PetIngestionJob creation failed: source is blank");
            return ResponseEntity.badRequest().body("source is required");
        }
        job.setStatus("PENDING");
        job.setCreatedAt(java.time.LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("petIngestionJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);

        logger.info("Created PetIngestionJob with technicalId: {}", technicalId);
        return ResponseEntity.status(201).body(job);
    }

    // GET /entity/petIngestionJob/{id} - retrieve PetIngestionJob
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("petIngestionJob", ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("PetIngestionJob not found");
        }
        PetIngestionJob job = objectMapper.treeToValue(node, PetIngestionJob.class);
        return ResponseEntity.ok(job);
    }

    // POST /entity/pet - create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet creation failed: name is blank");
            return ResponseEntity.badRequest().body("name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet creation failed: category is blank");
            return ResponseEntity.badRequest().body("category is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new ArrayList<>());
        }
        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        logger.info("Created Pet with technicalId: {}", technicalId);
        return ResponseEntity.status(201).body(pet);
    }

    // GET /entity/pet/{id} - retrieve Pet
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("pet", ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /entity/petStatusUpdate - create PetStatusUpdate
    @PostMapping("/petStatusUpdate")
    public ResponseEntity<?> createPetStatusUpdate(@RequestBody PetStatusUpdate update) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (update.getPetId() == null || update.getPetId().isBlank()) {
            logger.error("PetStatusUpdate creation failed: petId is blank");
            return ResponseEntity.badRequest().body("petId is required");
        }
        if (update.getNewStatus() == null || update.getNewStatus().isBlank()) {
            logger.error("PetStatusUpdate creation failed: newStatus is blank");
            return ResponseEntity.badRequest().body("newStatus is required");
        }

        UUID petUuid;
        try {
            petUuid = UUID.fromString(update.getPetId());
        } catch (IllegalArgumentException e) {
            logger.error("PetStatusUpdate creation failed: petId {} is invalid UUID", update.getPetId());
            return ResponseEntity.badRequest().body("petId is invalid UUID");
        }

        // Verify pet exists
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("pet", ENTITY_VERSION, petUuid);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            logger.error("PetStatusUpdate creation failed: petId {} does not exist", update.getPetId());
            return ResponseEntity.badRequest().body("petId does not exist");
        }

        update.setStatus("PENDING");
        update.setUpdatedAt(java.time.LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("petStatusUpdate", ENTITY_VERSION, update);
        UUID technicalId = idFuture.get();
        update.setTechnicalId(technicalId);

        logger.info("Created PetStatusUpdate with technicalId: {}", technicalId);
        return ResponseEntity.status(201).body(update);
    }

    // GET /entity/petStatusUpdate/{id} - retrieve PetStatusUpdate
    @GetMapping("/petStatusUpdate/{id}")
    public ResponseEntity<?> getPetStatusUpdate(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("petStatusUpdate", ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("PetStatusUpdate not found");
        }
        PetStatusUpdate update = objectMapper.treeToValue(node, PetStatusUpdate.class);
        return ResponseEntity.ok(update);
    }
}