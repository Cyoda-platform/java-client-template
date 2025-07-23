package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetUpdateJob;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // ------------------- PetUpdateJob Endpoints -------------------

    @PostMapping("/petUpdateJob")
    public ResponseEntity<?> createPetUpdateJob(@RequestBody PetUpdateJob job) {
        if (job == null) {
            log.error("Received null PetUpdateJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetUpdateJob cannot be null");
        }

        // Validate required fields
        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            log.error("Invalid sourceUrl for PetUpdateJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required and cannot be blank");
        }
        if (job.getJobId() == null || job.getJobId().isBlank()) {
            job.setJobId(UUID.randomUUID().toString());
        }
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }

        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "PetUpdateJob",
                    ENTITY_VERSION,
                    job
            );
            UUID technicalId = idFuture.join();
            job.setTechnicalId(technicalId);

            processPetUpdateJob(job);

            return ResponseEntity.status(HttpStatus.CREATED).body(job);
        } catch (Exception e) {
            log.error("Error creating PetUpdateJob", e);
            throw e;
        }
    }

    @GetMapping("/petUpdateJob/{id}")
    public ResponseEntity<?> getPetUpdateJob(@PathVariable("id") UUID id) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "PetUpdateJob",
                    ENTITY_VERSION,
                    id
            );
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("PetUpdateJob not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateJob not found");
            }

            PetUpdateJob job = entityService.getObjectMapper().treeToValue(node, PetUpdateJob.class);
            job.setTechnicalId(id);

            return ResponseEntity.ok(job);
        } catch (Exception e) {
            log.error("Error fetching PetUpdateJob with technicalId: {}", id, e);
            throw e;
        }
    }

    // ------------------- Pet Endpoints -------------------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Received null Pet");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet cannot be null");
        }

        // Validate mandatory fields
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            log.error("Invalid petId for Pet");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required and cannot be blank");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Invalid name for Pet");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name is required and cannot be blank");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Invalid category for Pet");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("category is required and cannot be blank");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }

        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "Pet",
                    ENTITY_VERSION,
                    pet
            );
            UUID technicalId = idFuture.join();
            pet.setTechnicalId(technicalId);

            processPet(pet);

            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            throw e;
        }
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable("id") UUID id) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "Pet",
                    ENTITY_VERSION,
                    id
            );
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("Pet not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }

            Pet pet = entityService.getObjectMapper().treeToValue(node, Pet.class);
            pet.setTechnicalId(id);

            return ResponseEntity.ok(pet);
        } catch (Exception e) {
            log.error("Error fetching Pet with technicalId: {}", id, e);
            throw e;
        }
    }

    // ------------------- Process Methods -------------------

    private void processPetUpdateJob(PetUpdateJob job) {
        log.info("Processing PetUpdateJob with technicalId: {}", job.getTechnicalId());

        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            log.error("PetUpdateJob {} has invalid sourceUrl", job.getTechnicalId());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("PROCESSING");

        try {
            // Simulate fetching pets from external Petstore API URL
            // For demonstration, create a mock pet list
            Pet fetchedPet = new Pet();
            fetchedPet.setPetId(UUID.randomUUID().toString());
            fetchedPet.setName("MockPet");
            fetchedPet.setCategory("cat");
            fetchedPet.setStatus("AVAILABLE");

            // Create pet via entityService
            CompletableFuture<UUID> petIdFuture = entityService.addItem(
                    "Pet",
                    ENTITY_VERSION,
                    fetchedPet
            );
            UUID petTechnicalId = petIdFuture.join();
            fetchedPet.setTechnicalId(petTechnicalId);

            processPet(fetchedPet);

            job.setStatus("COMPLETED");
            // Save updated job status as new version (event-driven, no update)
            entityService.addItem("PetUpdateJob", ENTITY_VERSION, job).join();

            log.info("PetUpdateJob {} completed successfully", job.getTechnicalId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            try {
                entityService.addItem("PetUpdateJob", ENTITY_VERSION, job).join();
            } catch (Exception ex) {
                log.error("Failed to save failed status for PetUpdateJob {}", job.getTechnicalId(), ex);
            }
            log.error("Error processing PetUpdateJob {}: {}", job.getTechnicalId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getPetId() == null || pet.getPetId().isBlank()
                || pet.getName() == null || pet.getName().isBlank()
                || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet {} failed validation during processing", pet.getTechnicalId());
            throw new IllegalArgumentException("Pet validation failed: mandatory fields missing");
        }

        log.info("Pet {} processed successfully with status {}", pet.getTechnicalId(), pet.getStatus());

        // Additional logic could be added here
    }
}