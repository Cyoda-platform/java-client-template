package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetUpdateJob;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // ------------------- PetUpdateJob Endpoints -------------------

    @PostMapping("/petUpdateJob")
    public ResponseEntity<?> createPetUpdateJob(@Valid @RequestBody PetUpdateJob job) throws JsonProcessingException {
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

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetUpdateJob",
                ENTITY_VERSION,
                job
        );
        UUID technicalId = idFuture.join();
        job.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/petUpdateJob/{id}")
    public ResponseEntity<?> getPetUpdateJob(@PathVariable("id") String id) throws JsonProcessingException {
        UUID uuid = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "PetUpdateJob",
                ENTITY_VERSION,
                uuid
        );
        ObjectNode node = itemFuture.join();
        if (node == null || node.isEmpty()) {
            log.error("PetUpdateJob not found with technicalId: {}", uuid);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateJob not found");
        }

        PetUpdateJob job = objectMapper.treeToValue(node, PetUpdateJob.class);
        job.setTechnicalId(uuid);

        return ResponseEntity.ok(job);
    }

    // ------------------- Pet Endpoints -------------------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws JsonProcessingException {
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

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Pet",
                ENTITY_VERSION,
                pet
        );
        UUID technicalId = idFuture.join();
        pet.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable("id") String id) throws JsonProcessingException {
        UUID uuid = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Pet",
                ENTITY_VERSION,
                uuid
        );
        ObjectNode node = itemFuture.join();
        if (node == null || node.isEmpty()) {
            log.error("Pet not found with technicalId: {}", uuid);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        Pet pet = objectMapper.treeToValue(node, Pet.class);
        pet.setTechnicalId(uuid);

        return ResponseEntity.ok(pet);
    }
}