package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // POST /controller/petIngestionJob - Create PetIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) throws Exception {
        if (job == null || job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            logger.error("Invalid PetIngestionJob creation request: missing sourceUrl");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required");
        }

        job.setCreatedAt(LocalDateTime.now());
        job.setStatus("PENDING");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetIngestionJob",
                ENTITY_VERSION,
                job
        );
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);

        try {
            processPetIngestionJob(job);
        } catch (Exception e) {
            logger.error("Error processing PetIngestionJob technicalId {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            entityService.updateItem("PetIngestionJob", ENTITY_VERSION, technicalId, job).get();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process PetIngestionJob");
        }

        logger.info("Created PetIngestionJob with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /controller/petIngestionJob/{id} - Retrieve PetIngestionJob by ID (id is String but mapped to technicalId UUID)
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for PetIngestionJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetIngestionJob", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.error("PetIngestionJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        PetIngestionJob job = node.traverse().readValueAs(PetIngestionJob.class);
        return ResponseEntity.ok(job);
    }

    // POST /controller/pet - Create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws Exception {
        if (pet == null) {
            logger.error("Pet creation request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet creation failed: name is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet creation failed: category is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE"); // default status
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        try {
            processPet(pet);
        } catch (Exception e) {
            logger.error("Error processing Pet technicalId {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process Pet");
        }

        logger.info("Created Pet with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{id} - Retrieve Pet by ID (UUID string)
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.error("Pet not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = node.traverse().readValueAs(Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /controller/pet/{id}/update - Create new version of Pet (event)
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet updatedPet) throws Exception {
        UUID originalTechnicalId;
        try {
            originalTechnicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> existingFuture = entityService.getItem("Pet", ENTITY_VERSION, originalTechnicalId);
        ObjectNode existingNode = existingFuture.get();
        if (existingNode == null || existingNode.isEmpty()) {
            logger.error("Cannot update Pet - not found with technicalId: {}", originalTechnicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet existingPet = existingNode.traverse().readValueAs(Pet.class);

        if (updatedPet == null) {
            logger.error("Update request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Updated Pet data is required");
        }
        if (updatedPet.getName() == null || updatedPet.getName().isBlank()) {
            logger.error("Updated Pet name is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (updatedPet.getCategory() == null || updatedPet.getCategory().isBlank()) {
            logger.error("Updated Pet category is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (updatedPet.getStatus() == null || updatedPet.getStatus().isBlank()) {
            updatedPet.setStatus(existingPet.getStatus());
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, updatedPet);
        UUID newTechnicalId = idFuture.get();
        updatedPet.setTechnicalId(newTechnicalId);

        try {
            processPet(updatedPet);
        } catch (Exception e) {
            logger.error("Error processing updated Pet technicalId {}: {}", newTechnicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process updated Pet");
        }

        logger.info("Created updated Pet version with technicalId: {}", newTechnicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(updatedPet);
    }

    // POST /controller/pet/{id}/deactivate - Create Pet deactivation event
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) throws Exception {
        UUID originalTechnicalId;
        try {
            originalTechnicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> existingFuture = entityService.getItem("Pet", ENTITY_VERSION, originalTechnicalId);
        ObjectNode existingNode = existingFuture.get();
        if (existingNode == null || existingNode.isEmpty()) {
            logger.error("Cannot deactivate Pet - not found with technicalId: {}", originalTechnicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet existingPet = existingNode.traverse().readValueAs(Pet.class);

        Pet deactivatedPet = new Pet();
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setCategory(existingPet.getCategory());
        deactivatedPet.setPhotoUrls(existingPet.getPhotoUrls());
        deactivatedPet.setTags(existingPet.getTags());
        deactivatedPet.setStatus("DEACTIVATED");

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, deactivatedPet);
        UUID deactivatedTechnicalId = idFuture.get();
        deactivatedPet.setTechnicalId(deactivatedTechnicalId);

        logger.info("Deactivated Pet with new version technicalId: {}", deactivatedTechnicalId);
        return ResponseEntity.ok("Pet deactivated with new version technicalId: " + deactivatedTechnicalId);
    }

    private void processPetIngestionJob(PetIngestionJob job) throws Exception {
        logger.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());

        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            job.setStatus("FAILED");
            entityService.updateItem("PetIngestionJob", ENTITY_VERSION, job.getTechnicalId(), job).get();
            logger.error("PetIngestionJob failed validation: sourceUrl is blank");
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        job.setStatus("PROCESSING");
        entityService.updateItem("PetIngestionJob", ENTITY_VERSION, job.getTechnicalId(), job).get();

        // Simulate fetching data from Petstore API (simplified for prototype)
        Pet newPet = new Pet();
        newPet.setName("SamplePetFromIngestion");
        newPet.setCategory("cat");
        newPet.setStatus("AVAILABLE");

        CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", ENTITY_VERSION, newPet);
        UUID petTechnicalId = petIdFuture.get();
        newPet.setTechnicalId(petTechnicalId);

        processPet(newPet);

        job.setStatus("COMPLETED");
        entityService.updateItem("PetIngestionJob", ENTITY_VERSION, job.getTechnicalId(), job).get();

        logger.info("Completed processing PetIngestionJob with technicalId: {}", job.getTechnicalId());
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (!pet.isValid()) {
            logger.error("Invalid Pet entity with technicalId: {}", pet.getTechnicalId());
            throw new IllegalArgumentException("Pet entity validation failed");
        }

        if ("cat".equalsIgnoreCase(pet.getCategory())) {
            if (pet.getTags() == null) {
                pet.setTags(new ArrayList<>());
            }
            if (!pet.getTags().contains("feline")) {
                pet.getTags().add("feline");
            }
        }

        logger.info("Pet processing complete for technicalId: {}", pet.getTechnicalId());
    }
}