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

        processPurrfectPetsJob(job);

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

        processPet(pet);

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

        processFavorite(favorite);

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

    private void processPurrfectPetsJob(PurrfectPetsJob job) throws ExecutionException, InterruptedException {
        log.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());
        if (job.getPetType() == null || job.getPetType().isBlank()) {
            log.error("Job petType is invalid");
            job.setStatus("FAILED");
            // Update job status as a new entity version
            entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job).get();
            return;
        }
        job.setStatus("PROCESSING");
        entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job).get();

        // Simulate fetching pets from external source and creating them
        List<Pet> fetchedPets = new ArrayList<>();

        Pet pet1 = new Pet();
        pet1.setPetId("pet-" + petIdCounter.get());
        pet1.setName("Whiskers");
        pet1.setType(job.getPetType());
        pet1.setAge(3);
        pet1.setStatus("AVAILABLE");
        CompletableFuture<UUID> pet1IdFuture = entityService.addItem("Pet", ENTITY_VERSION, pet1);
        UUID pet1Id = pet1IdFuture.get();
        pet1.setTechnicalId(pet1Id);
        fetchedPets.add(pet1);

        Pet pet2 = new Pet();
        pet2.setPetId("pet-" + petIdCounter.get());
        pet2.setName("Fido");
        pet2.setType(job.getPetType());
        pet2.setAge(5);
        pet2.setStatus("AVAILABLE");
        CompletableFuture<UUID> pet2IdFuture = entityService.addItem("Pet", ENTITY_VERSION, pet2);
        UUID pet2Id = pet2IdFuture.get();
        pet2.setTechnicalId(pet2Id);
        fetchedPets.add(pet2);

        job.setStatus("COMPLETED");
        entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job).get();

        log.info("Completed PurrfectPetsJob with technicalId: {}. Pets fetched: {}", job.getTechnicalId(), fetchedPets.size());
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        if (!pet.isValid()) {
            log.error("Invalid pet data for pet technicalId: {}", pet.getTechnicalId());
            return;
        }
        if ("ADOPTED".equalsIgnoreCase(pet.getStatus())) {
            log.info("Pet {} is adopted.", pet.getName());
        }
    }

    private void processFavorite(Favorite favorite) {
        log.info("Processing Favorite with technicalId: {}", favorite.getTechnicalId());
        if (favorite.getUserId() == null || favorite.getUserId().isBlank()) {
            log.error("Favorite userId is invalid");
            return;
        }
        if (favorite.getPetId() == null || favorite.getPetId().isBlank()) {
            log.error("Favorite petId is invalid");
            return;
        }
        log.info("User {} favorited pet {}", favorite.getUserId(), favorite.getPetId());
    }
}