package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetAdoptionJob;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.JobStatusEnum;
import com.java_template.application.entity.PetStatusEnum;
import com.java_template.application.entity.RequestStatusEnum;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final AtomicLong petAdoptionJobIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // POST /entity/petAdoptionJob - create PetAdoptionJob
    @PostMapping("/petAdoptionJob")
    public ResponseEntity<?> createPetAdoptionJob(@RequestBody PetAdoptionJob job) throws ExecutionException, InterruptedException {
        if (job == null) {
            logger.error("PetAdoptionJob payload is null");
            return ResponseEntity.badRequest().body("PetAdoptionJob payload cannot be null");
        }
        if (job.getId() == null || job.getId().isBlank()) {
            job.setId("job-" + petAdoptionJobIdCounter.getAndIncrement());
        }
        if (job.getStatus() == null) {
            job.setStatus(JobStatusEnum.PENDING);
        }
        if (!job.isValid()) {
            logger.error("Invalid PetAdoptionJob data: {}", job);
            return ResponseEntity.badRequest().body("Invalid PetAdoptionJob data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("PetAdoptionJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        logger.info("Created PetAdoptionJob with technicalId {}", technicalId);

        // processPetAdoptionJob removed

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /entity/petAdoptionJob/{id} - retrieve PetAdoptionJob
    @GetMapping("/petAdoptionJob/{id}")
    public ResponseEntity<?> getPetAdoptionJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }

        // Search by id field (business id) in PetAdoptionJob entities
        Condition cond = Condition.of("$.id", "EQUALS", id);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("PetAdoptionJob", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();

        if (items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetAdoptionJob not found for ID: " + id);
        }

        ObjectNode jobNode = (ObjectNode) items.get(0);
        PetAdoptionJob job = convertObjectNodeToPetAdoptionJob(jobNode);

        return ResponseEntity.ok(job);
    }

    // POST /entity/pet - create Pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null) {
            logger.error("Pet payload is null");
            return ResponseEntity.badRequest().body("Pet payload cannot be null");
        }
        if (pet.getId() == null || pet.getId().isBlank()) {
            pet.setId("pet-" + petIdCounter.getAndIncrement());
        }
        if (pet.getStatus() == null) {
            pet.setStatus(PetStatusEnum.AVAILABLE);
        }
        if (!pet.isValid()) {
            logger.error("Invalid Pet data: {}", pet);
            return ResponseEntity.badRequest().body("Invalid Pet data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        logger.info("Created Pet with technicalId {}", technicalId);

        // processPet removed

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /entity/pet/{id} - retrieve Pet
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }

        Condition cond = Condition.of("$.id", "EQUALS", id);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();

        if (items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found for ID: " + id);
        }

        ObjectNode petNode = (ObjectNode) items.get(0);
        Pet pet = convertObjectNodeToPet(petNode);

        return ResponseEntity.ok(pet);
    }

    // POST /entity/adoptionRequest - create AdoptionRequest
    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest request) throws ExecutionException, InterruptedException {
        if (request == null) {
            logger.error("AdoptionRequest payload is null");
            return ResponseEntity.badRequest().body("AdoptionRequest payload cannot be null");
        }
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId("request-" + adoptionRequestIdCounter.getAndIncrement());
        }
        if (request.getStatus() == null) {
            request.setStatus(RequestStatusEnum.PENDING);
        }
        if (!request.isValid()) {
            logger.error("Invalid AdoptionRequest data: {}", request);
            return ResponseEntity.badRequest().body("Invalid AdoptionRequest data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("AdoptionRequest", ENTITY_VERSION, request);
        UUID technicalId = idFuture.get();
        logger.info("Created AdoptionRequest with technicalId {}", technicalId);

        // processAdoptionRequest removed

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    // GET /entity/adoptionRequest/{id} - retrieve AdoptionRequest
    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) throws ExecutionException, InterruptedException {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }

        Condition cond = Condition.of("$.id", "EQUALS", id);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("AdoptionRequest", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();

        if (items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found for ID: " + id);
        }

        ObjectNode requestNode = (ObjectNode) items.get(0);
        AdoptionRequest request = convertObjectNodeToAdoptionRequest(requestNode);

        return ResponseEntity.ok(request);
    }

    // Helper methods to convert ObjectNode to entities

    private PetAdoptionJob convertObjectNodeToPetAdoptionJob(ObjectNode node) {
        PetAdoptionJob job = new PetAdoptionJob();
        job.setId(getText(node, "id"));
        job.setPetId(getText(node, "petId"));
        job.setAdopterName(getText(node, "adopterName"));
        job.setStatus(JobStatusEnum.valueOf(getText(node, "status")));
        // add other fields as needed
        return job;
    }

    private Pet convertObjectNodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        pet.setId(getText(node, "id"));
        pet.setName(getText(node, "name"));
        pet.setCategory(getText(node, "category"));
        pet.setStatus(PetStatusEnum.valueOf(getText(node, "status")));
        // add other fields as needed
        return pet;
    }

    private AdoptionRequest convertObjectNodeToAdoptionRequest(ObjectNode node) {
        AdoptionRequest req = new AdoptionRequest();
        req.setId(getText(node, "id"));
        req.setPetId(getText(node, "petId"));
        req.setRequesterName(getText(node, "requesterName"));
        if (node.has("requestDate") && !node.get("requestDate").isNull()) {
            req.setRequestDate(new Date(node.get("requestDate").asLong()));
        }
        req.setStatus(RequestStatusEnum.valueOf(getText(node, "status")));
        // add other fields as needed
        return req;
    }

    private String getText(ObjectNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }
}