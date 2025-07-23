package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PET_JOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";

    // POST /controller/petJob - Create PetJob entity
    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException {
        if (petJob == null || petJob.getType() == null || petJob.getType().isBlank()) {
            log.error("Invalid PetJob: missing type");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: type");
        }
        petJob.setStatus("PENDING");

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_JOB_ENTITY, ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);

        log.info("Created PetJob with technicalId: {}", technicalId);

        processPetJob(petJob);

        // After processing, update PetJob status by creating a new version (create new entity)
        CompletableFuture<UUID> updatedIdFuture = entityService.addItem(PET_JOB_ENTITY, ENTITY_VERSION, petJob);
        UUID updatedTechnicalId = updatedIdFuture.get();
        petJob.setTechnicalId(updatedTechnicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", updatedTechnicalId, "status", petJob.getStatus()));
    }

    // GET /controller/petJob/{id} - Retrieve PetJob by technicalId
    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for PetJob id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_JOB_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode result = itemFuture.get();
        if (result == null || result.isEmpty()) {
            log.error("PetJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(result);
    }

    // POST /controller/pet - Create Pet entity
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Invalid Pet: missing name or category");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields: name and category");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        log.info("Created Pet with technicalId: {}", technicalId);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{id} - Retrieve Pet by technicalId
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid UUID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode result = itemFuture.get();
        if (result == null || result.isEmpty()) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(result);
    }

    // Business logic for processing PetJob entity
    private void processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        log.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());
        if (petJob.getType().equalsIgnoreCase("AddPet")) {
            Map<String, Object> payload = petJob.getPayload();
            if (payload == null) {
                log.error("PetJob payload is null for AddPet");
                petJob.setStatus("FAILED");
                return;
            }
            String name = (String) payload.get("name");
            String category = (String) payload.get("category");
            if (name == null || name.isBlank() || category == null || category.isBlank()) {
                log.error("Invalid Pet data in PetJob payload");
                petJob.setStatus("FAILED");
                return;
            }
            Pet newPet = new Pet();
            newPet.setName(name);
            newPet.setCategory(category);
            Object tagsObj = payload.get("tags");
            if (tagsObj instanceof List<?>) {
                List<String> tags = new ArrayList<>();
                for (Object o : (List<?>) tagsObj) {
                    if (o instanceof String) tags.add((String) o);
                }
                newPet.setTags(tags);
            }
            Object photosObj = payload.get("photoUrls");
            if (photosObj instanceof List<?>) {
                List<String> photos = new ArrayList<>();
                for (Object o : (List<?>) photosObj) {
                    if (o instanceof String) photos.add((String) o);
                }
                newPet.setPhotoUrls(photos);
            }
            newPet.setStatus("AVAILABLE");

            CompletableFuture<UUID> petIdFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, newPet);
            UUID petTechnicalId = petIdFuture.get();
            newPet.setTechnicalId(petTechnicalId);

            log.info("Added new Pet with technicalId: {}", petTechnicalId);
            petJob.setStatus("COMPLETED");
        } else if (petJob.getType().equalsIgnoreCase("UpdatePetStatus")) {
            Map<String, Object> payload = petJob.getPayload();
            if (payload == null) {
                log.error("PetJob payload is null for UpdatePetStatus");
                petJob.setStatus("FAILED");
                return;
            }
            String petIdStr = (String) payload.get("id");
            String newStatus = (String) payload.get("status");
            if (petIdStr == null || petIdStr.isBlank() || newStatus == null || newStatus.isBlank()) {
                log.error("Invalid Pet ID or status in PetJob payload");
                petJob.setStatus("FAILED");
                return;
            }
            UUID petId;
            try {
                petId = UUID.fromString(petIdStr);
            } catch (IllegalArgumentException e) {
                log.error("Invalid UUID format for Pet id in PetJob payload: {}", petIdStr);
                petJob.setStatus("FAILED");
                return;
            }
            CompletableFuture<ObjectNode> existingPetFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, petId);
            ObjectNode existingPetNode = existingPetFuture.get();
            if (existingPetNode == null || existingPetNode.isEmpty()) {
                log.error("Pet not found for update with technicalId: {}", petIdStr);
                petJob.setStatus("FAILED");
                return;
            }

            Pet updatedPet = new Pet();
            updatedPet.setName(existingPetNode.has("name") && !existingPetNode.get("name").isNull() ? existingPetNode.get("name").asText() : null);
            updatedPet.setCategory(existingPetNode.has("category") && !existingPetNode.get("category").isNull() ? existingPetNode.get("category").asText() : null);
            if (existingPetNode.has("tags") && existingPetNode.get("tags").isArray()) {
                List<String> tags = new ArrayList<>();
                existingPetNode.get("tags").forEach(node -> {
                    if (!node.isNull()) tags.add(node.asText());
                });
                updatedPet.setTags(tags);
            }
            if (existingPetNode.has("photoUrls") && existingPetNode.get("photoUrls").isArray()) {
                List<String> photos = new ArrayList<>();
                existingPetNode.get("photoUrls").forEach(node -> {
                    if (!node.isNull()) photos.add(node.asText());
                });
                updatedPet.setPhotoUrls(photos);
            }
            updatedPet.setStatus(newStatus);

            CompletableFuture<UUID> updatedPetIdFuture = entityService.addItem(PET_ENTITY, ENTITY_VERSION, updatedPet);
            UUID updatedPetTechnicalId = updatedPetIdFuture.get();
            updatedPet.setTechnicalId(updatedPetTechnicalId);

            log.info("Updated Pet status for new Pet technicalId: {}", updatedPetTechnicalId);
            petJob.setStatus("COMPLETED");
        } else {
            log.error("Unknown PetJob type: {}", petJob.getType());
            petJob.setStatus("FAILED");
        }
    }

    // Business logic for processing Pet entity
    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        if (pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet validation failed for technicalId: {}", pet.getTechnicalId());
            throw new IllegalArgumentException("Pet must have non-blank name and category");
        }
        // Future scope: indexing, notifications, other processing workflows
        log.info("Pet processing completed for technicalId: {}", pet.getTechnicalId());
    }
}