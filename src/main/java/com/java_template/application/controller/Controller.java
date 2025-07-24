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

        processPetIngestionJob(job);

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

        processPet(pet);

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

        processPetStatusUpdate(update);

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

    // Process methods with real business logic

    private void processPetIngestionJob(PetIngestionJob job) throws ExecutionException, InterruptedException {
        logger.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());
        // 1. Update status to PROCESSING
        job.setStatus("PROCESSING");
        entityService.updateItem("PetIngestionJob", ENTITY_VERSION, job.getTechnicalId(), job).get();

        try {
            // 2. Simulate fetching pet data from external Petstore API
            // Here, for prototype, we simulate adding a pet
            Pet newPet = new Pet();
            newPet.setName("Simulated Pet");
            newPet.setCategory("cat");
            newPet.setPhotoUrls(Collections.emptyList());
            newPet.setTags(Collections.singletonList("simulated"));
            newPet.setStatus("AVAILABLE");

            CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", ENTITY_VERSION, newPet);
            UUID petTechnicalId = petIdFuture.get();
            newPet.setTechnicalId(petTechnicalId);
            processPet(newPet);

            // 3. Update job status to COMPLETED
            job.setStatus("COMPLETED");
            entityService.updateItem("PetIngestionJob", ENTITY_VERSION, job.getTechnicalId(), job).get();

            logger.info("PetIngestionJob {} completed successfully", job.getTechnicalId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            entityService.updateItem("PetIngestionJob", ENTITY_VERSION, job.getTechnicalId(), job).get();
            logger.error("PetIngestionJob {} failed: {}", job.getTechnicalId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) throws ExecutionException, InterruptedException {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        // Enrichment example: add a tag "processed"
        if (!pet.getTags().contains("processed")) {
            pet.getTags().add("processed");
        }
        entityService.updateItem("Pet", ENTITY_VERSION, pet.getTechnicalId(), pet).get();
        logger.info("Pet {} processed and enriched", pet.getTechnicalId());
    }

    private void processPetStatusUpdate(PetStatusUpdate update) throws ExecutionException, InterruptedException {
        logger.info("Processing PetStatusUpdate with technicalId: {}", update.getTechnicalId());

        UUID petUuid = UUID.fromString(update.getPetId());
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, petUuid);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            logger.error("PetStatusUpdate processing failed: Pet {} not found", update.getPetId());
            update.setStatus("FAILED");
            entityService.updateItem("PetStatusUpdate", ENTITY_VERSION, update.getTechnicalId(), update).get();
            return;
        }

        // Create new Pet entity representing status update (immutable)
        Pet updatedPet = new Pet();
        updatedPet.setName(petNode.get("name").asText());
        updatedPet.setCategory(petNode.get("category").asText());
        List<String> photoUrls = new ArrayList<>();
        if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
            petNode.get("photoUrls").forEach(n -> photoUrls.add(n.asText()));
        }
        updatedPet.setPhotoUrls(photoUrls);

        List<String> tags = new ArrayList<>();
        if (petNode.has("tags") && petNode.get("tags").isArray()) {
            petNode.get("tags").forEach(n -> tags.add(n.asText()));
        }
        updatedPet.setTags(tags);

        updatedPet.setStatus(update.getNewStatus());

        CompletableFuture<UUID> newPetIdFuture = entityService.addItem("Pet", ENTITY_VERSION, updatedPet);
        UUID newPetTechnicalId = newPetIdFuture.get();
        updatedPet.setTechnicalId(newPetTechnicalId);

        update.setStatus("PROCESSED");
        entityService.updateItem("PetStatusUpdate", ENTITY_VERSION, update.getTechnicalId(), update).get();

        logger.info("PetStatusUpdate {} processed: Pet {} status updated to {}", update.getTechnicalId(), newPetTechnicalId, update.getNewStatus());
    }
}