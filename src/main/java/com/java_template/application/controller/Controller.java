package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.PetStatusUpdate;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // POST /entity/petIngestionJob - create PetIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) throws ExecutionException, InterruptedException {
        if (job.getSource() == null || job.getSource().isBlank()) {
            logger.error("PetIngestionJob creation failed: source is blank");
            return ResponseEntity.badRequest().body("source is required");
        }
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());

        CompletableFuture<UUID> idFuture = entityService.addItem("PetIngestionJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);

        // processPetIngestionJob(job); // Removed process method call

        logger.info("Created PetIngestionJob with technicalId: {}", technicalId);
        return ResponseEntity.status(201).body(job);
    }

    // GET /entity/petIngestionJob/{id} - retrieve PetIngestionJob
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetIngestionJob", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("PetIngestionJob not found");
        }
        return ResponseEntity.ok(node);
    }

    // POST /entity/pet - create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
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

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        // processPet(pet); // Removed process method call

        logger.info("Created Pet with technicalId: {}", technicalId);
        return ResponseEntity.status(201).body(pet);
    }

    // GET /entity/pet/{id} - retrieve Pet
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("Pet not found");
        }
        return ResponseEntity.ok(node);
    }

    // POST /entity/petStatusUpdate - create PetStatusUpdate
    @PostMapping("/petStatusUpdate")
    public ResponseEntity<?> createPetStatusUpdate(@RequestBody PetStatusUpdate update) throws ExecutionException, InterruptedException {
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
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, petUuid);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            logger.error("PetStatusUpdate creation failed: petId {} does not exist", update.getPetId());
            return ResponseEntity.badRequest().body("petId does not exist");
        }

        update.setStatus("PENDING");
        update.setUpdatedAt(new Date());

        CompletableFuture<UUID> idFuture = entityService.addItem("PetStatusUpdate", ENTITY_VERSION, update);
        UUID technicalId = idFuture.get();
        update.setTechnicalId(technicalId);

        // processPetStatusUpdate(update); // Removed process method call

        logger.info("Created PetStatusUpdate with technicalId: {}", technicalId);
        return ResponseEntity.status(201).body(update);
    }

    // GET /entity/petStatusUpdate/{id} - retrieve PetStatusUpdate
    @GetMapping("/petStatusUpdate/{id}")
    public ResponseEntity<?> getPetStatusUpdate(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetStatusUpdate", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("PetStatusUpdate not found");
        }
        return ResponseEntity.ok(node);
    }

    // Other CRUD operations and endpoints remain intact
}