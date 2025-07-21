package com.java_template.application.controller;

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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private final AtomicLong petIdCounter = new AtomicLong(1); // used internally for simulated pets in job processing

    // POST /entities/purrfectPetsJob
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) throws ExecutionException, InterruptedException {
        if (job == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
        }
        if (job.getPetType() == null || job.getPetType().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petType is required");
        }
        job.setStatus("PENDING");
        if (!job.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid job data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /entities/purrfectPetsJob/{id}
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", id));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("PurrfectPetsJob", ENTITY_VERSION, condition);
        ArrayNode items = filteredItemsFuture.get();
        if (items == null || items.size() == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        ObjectNode node = (ObjectNode) items.get(0);
        PurrfectPetsJob job = node.traverse().readValueAs(PurrfectPetsJob.class);
        return ResponseEntity.ok(job);
    }

    // POST /entities/pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }
        if (!pet.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data");
        }
        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /entities/pet/{id}
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", id));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition);
        ArrayNode items = filteredItemsFuture.get();
        if (items == null || items.size() == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        ObjectNode node = (ObjectNode) items.get(0);
        Pet pet = node.traverse().readValueAs(Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /entities/favorite
    @PostMapping("/favorite")
    public ResponseEntity<?> createFavorite(@RequestBody Favorite favorite) throws ExecutionException, InterruptedException {
        if (favorite == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
        }
        if (favorite.getUserId() == null || favorite.getUserId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("userId is required");
        }
        if (favorite.getPetId() == null || favorite.getPetId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("petId is required");
        }
        if (favorite.getStatus() == null || favorite.getStatus().isBlank()) {
            favorite.setStatus("ACTIVE");
        }
        if (!favorite.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid favorite data");
        }
        CompletableFuture<UUID> idFuture = entityService.addItem("Favorite", ENTITY_VERSION, favorite);
        UUID technicalId = idFuture.get();
        favorite.setTechnicalId(technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(favorite);
    }

    // GET /entities/favorite/{id}
    @GetMapping("/favorite/{id}")
    public ResponseEntity<?> getFavorite(@PathVariable String id) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", id));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Favorite", ENTITY_VERSION, condition);
        ArrayNode items = filteredItemsFuture.get();
        if (items == null || items.size() == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Favorite not found");
        }
        ObjectNode node = (ObjectNode) items.get(0);
        Favorite favorite = node.traverse().readValueAs(Favorite.class);
        return ResponseEntity.ok(favorite);
    }
}