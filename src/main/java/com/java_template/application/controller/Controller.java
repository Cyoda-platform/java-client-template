package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.JobStatusEnum;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetAdoptionJob;
import com.java_template.application.entity.PetStatusEnum;
import com.java_template.application.entity.RequestStatusEnum;
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
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // Local cache for Pet entity only (utility entity, minor logic)
    private final Map<String, Pet> petCache = new HashMap<>();
    private long petIdCounter = 1;

    // POST /entity/petAdoptionJob - create PetAdoptionJob
    @PostMapping("/petAdoptionJob")
    public ResponseEntity<?> createPetAdoptionJob(@RequestBody PetAdoptionJob job) throws ExecutionException, InterruptedException {
        if (job == null) {
            log.error("PetAdoptionJob payload is null");
            return ResponseEntity.badRequest().body("PetAdoptionJob payload cannot be null");
        }
        if (job.getId() == null || job.getId().isBlank()) {
            job.setId("job-" + UUID.randomUUID());
        }
        if (job.getStatus() == null) {
            job.setStatus(JobStatusEnum.PENDING);
        }
        if (!job.isValid()) {
            log.error("Invalid PetAdoptionJob data: {}", job);
            return ResponseEntity.badRequest().body("Invalid PetAdoptionJob data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetAdoptionJob",
                ENTITY_VERSION,
                job
        );
        UUID technicalId = idFuture.get();
        // Retrieve the persisted job with technicalId to ensure consistency
        CompletableFuture<ObjectNode> jobNodeFuture = entityService.getItem("PetAdoptionJob", ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobNodeFuture.get();

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /entity/petAdoptionJob/{id} - retrieve PetAdoptionJob by business id (id field)
    @GetMapping("/petAdoptionJob/{id}")
    public ResponseEntity<?> getPetAdoptionJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("PetAdoptionJob", ENTITY_VERSION, condition, true);
        ArrayNode results = filteredItemsFuture.get();
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetAdoptionJob not found for ID: " + id);
        }
        ObjectNode jobNode = (ObjectNode) results.get(0);
        return ResponseEntity.ok(jobNode);
    }

    // POST /entity/pet - create Pet (keep local cache, minor logic)
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Pet payload is null");
            return ResponseEntity.badRequest().body("Pet payload cannot be null");
        }
        if (pet.getId() == null || pet.getId().isBlank()) {
            pet.setId("pet-" + petIdCounter++);
        }
        if (pet.getStatus() == null) {
            pet.setStatus(PetStatusEnum.AVAILABLE);
        }
        if (!pet.isValid()) {
            log.error("Invalid Pet data: {}", pet);
            return ResponseEntity.badRequest().body("Invalid Pet data");
        }
        petCache.put(pet.getId(), pet);
        log.info("Created Pet with ID {}", pet.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /entity/pet/{id} - retrieve Pet from local cache
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found for ID: " + id);
        }
        return ResponseEntity.ok(pet);
    }

    // POST /entity/adoptionRequest - create AdoptionRequest
    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest request) throws ExecutionException, InterruptedException {
        if (request == null) {
            log.error("AdoptionRequest payload is null");
            return ResponseEntity.badRequest().body("AdoptionRequest payload cannot be null");
        }
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId("request-" + UUID.randomUUID());
        }
        if (request.getStatus() == null) {
            request.setStatus(RequestStatusEnum.PENDING);
        }
        if (!request.isValid()) {
            log.error("Invalid AdoptionRequest data: {}", request);
            return ResponseEntity.badRequest().body("Invalid AdoptionRequest data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "AdoptionRequest",
                ENTITY_VERSION,
                request
        );
        UUID technicalId = idFuture.get();

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    // GET /entity/adoptionRequest/{id} - retrieve AdoptionRequest by business id
    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) throws ExecutionException, InterruptedException {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("ID cannot be null or blank");
        }
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("AdoptionRequest", ENTITY_VERSION, condition, true);
        ArrayNode results = filteredItemsFuture.get();
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found for ID: " + id);
        }
        ObjectNode requestNode = (ObjectNode) results.get(0);
        return ResponseEntity.ok(requestNode);
    }

}