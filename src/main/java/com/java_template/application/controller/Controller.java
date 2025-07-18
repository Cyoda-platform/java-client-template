package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Favorite;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // ----------------------- PurrfectPetsJob Endpoints -----------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createPurrfectPetsJob(@Valid @RequestBody PurrfectPetsJob job) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (job == null) {
            log.error("Received null PurrfectPetsJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PurrfectPetsJob data is required");
        }
        if (job.getOperationType() == null || job.getOperationType().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("OperationType is required");
        }

        job.setStatus("PENDING");
        CompletableFuture<UUID> jobIdFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
        UUID technicalId = jobIdFuture.get();
        job.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", technicalId.toString(), "status", job.getStatus()));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid UUID format for job id");
        }
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("PurrfectPetsJob", ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PurrfectPetsJob not found with id " + id);
        }
        // Convert ObjectNode to PurrfectPetsJob entity if needed
        PurrfectPetsJob job = objectMapper.treeToValue(jobNode, PurrfectPetsJob.class);
        return ResponseEntity.ok(job);
    }

    // ----------------------- Pet Endpoints -----------------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet status is required");
        }

        CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = petIdFuture.get();
        pet.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("petId", technicalId.toString(), "status", pet.getStatus()));
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid UUID format for pet id");
        }
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found with id " + id);
        }
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // ----------------------- Favorite Endpoints -----------------------

    @PostMapping("/favorites")
    public ResponseEntity<?> createFavorite(@Valid @RequestBody Favorite favorite) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (favorite == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Favorite data is required");
        }
        if (favorite.getUserId() == null || favorite.getUserId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("UserId is required");
        }
        if (favorite.getPetId() == null || favorite.getPetId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetId is required");
        }
        if (favorite.getStatus() == null || favorite.getStatus().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Favorite status is required");
        }

        // Check that petId exists by querying Pet entityService
        UUID petTechnicalId;
        try {
            petTechnicalId = UUID.fromString(favorite.getPetId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid UUID format for petId in favorite");
        }
        CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem("Pet", ENTITY_VERSION, petTechnicalId);
        ObjectNode petNode = petNodeFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Favorite processing failed: Pet ID {} does not exist", favorite.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet ID does not exist: " + favorite.getPetId());
        }

        CompletableFuture<UUID> favoriteIdFuture = entityService.addItem("Favorite", ENTITY_VERSION, favorite);
        UUID technicalId = favoriteIdFuture.get();
        favorite.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("favoriteId", technicalId.toString(), "status", favorite.getStatus()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavoritesByUser(@RequestParam @NotBlank String userId) throws ExecutionException, InterruptedException {
        Condition userCondition = Condition.of("$.userId", "EQUALS", userId);
        Condition statusCondition = Condition.of("$.status", "EQUALS", "ACTIVE");
        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", userCondition, statusCondition);

        CompletableFuture<ArrayNode> favoritesFuture = entityService.getItemsByCondition("Favorite", ENTITY_VERSION, conditionRequest);
        ArrayNode favoritesNode = favoritesFuture.get();

        return ResponseEntity.ok(favoritesNode);
    }
}