package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.application.entity.Pet;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /controller/purrfectPetsJob - create new job and trigger processing
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) throws Exception {
        if (job == null) {
            return ResponseEntity.badRequest().body("Job cannot be null");
        }
        // Assign business ID if missing
        if (job.getId() == null || job.getId().isBlank()) {
            job.setId("job-" + purrfectPetsJobIdCounter.getAndIncrement());
        }
        // Set createdAt if missing
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }
        job.setStatus(PurrfectPetsJob.StatusEnum.PENDING);

        if (!job.isValid()) {
            return ResponseEntity.badRequest().body("Invalid job data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        logger.info("Created PurrfectPetsJob with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    // GET /controller/purrfectPetsJob/{id} - get job by ID
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) throws Exception {
        // Search by condition on id field
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("PurrfectPetsJob", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();
        if (items == null || items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        ObjectNode objNode = (ObjectNode) items.get(0);
        // Convert ObjectNode to PurrfectPetsJob using Jackson or manual mapping
        PurrfectPetsJob job = objNode.traverse().readValueAs(PurrfectPetsJob.class);
        return ResponseEntity.ok(job);
    }

    // POST /controller/pet - create new pet and trigger processing
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws Exception {
        if (pet == null) {
            return ResponseEntity.badRequest().body("Pet cannot be null");
        }
        if (pet.getId() == null || pet.getId().isBlank()) {
            pet.setId("pet-" + petIdCounter.getAndIncrement());
        }
        if (pet.getCreatedAt() == null) {
            pet.setCreatedAt(LocalDateTime.now());
        }
        if (pet.getLifecycleStatus() == null) {
            pet.setLifecycleStatus(Pet.StatusEnum.NEW);
        }
        if (!pet.isValid()) {
            return ResponseEntity.badRequest().body("Invalid pet data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        logger.info("Created Pet with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{id} - get pet by ID
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws Exception {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();
        if (items == null || items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        ObjectNode objNode = (ObjectNode) items.get(0);
        Pet pet = objNode.traverse().readValueAs(Pet.class);
        return ResponseEntity.ok(pet);
    }

    // POST /controller/purrfectPetsJob/{id}/update - create new job version (optional, avoid if possible)
    @PostMapping("/purrfectPetsJob/{id}/update")
    public ResponseEntity<?> updatePurrfectPetsJob(@PathVariable String id, @RequestBody PurrfectPetsJob updatedJob) throws Exception {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("PurrfectPetsJob", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();
        if (items == null || items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }

        updatedJob.setId("job-" + purrfectPetsJobIdCounter.getAndIncrement());
        if (updatedJob.getCreatedAt() == null) {
            updatedJob.setCreatedAt(LocalDateTime.now());
        }
        if (!updatedJob.isValid()) {
            return ResponseEntity.badRequest().body("Invalid updated job data");
        }
        updatedJob.setStatus(PurrfectPetsJob.StatusEnum.PENDING);

        CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, updatedJob);
        UUID technicalId = idFuture.get();
        logger.info("Created new version of PurrfectPetsJob with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(updatedJob);
    }

    // POST /controller/pet/{id}/update - create new pet version (optional)
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet updatedPet) throws Exception {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();
        if (items == null || items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        updatedPet.setId("pet-" + petIdCounter.getAndIncrement());
        if (updatedPet.getCreatedAt() == null) {
            updatedPet.setCreatedAt(LocalDateTime.now());
        }
        if (!updatedPet.isValid()) {
            return ResponseEntity.badRequest().body("Invalid updated pet data");
        }
        if (updatedPet.getLifecycleStatus() == null) {
            updatedPet.setLifecycleStatus(Pet.StatusEnum.NEW);
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, updatedPet);
        UUID technicalId = idFuture.get();
        logger.info("Created new version of Pet with technicalId: {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(updatedPet);
    }

    // POST /controller/purrfectPetsJob/{id}/deactivate - create deactivation event (optional)
    @PostMapping("/purrfectPetsJob/{id}/deactivate")
    public ResponseEntity<?> deactivatePurrfectPetsJob(@PathVariable String id) throws Exception {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("PurrfectPetsJob", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();
        if (items == null || items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        ObjectNode objNode = (ObjectNode) items.get(0);
        PurrfectPetsJob existingJob = objNode.traverse().readValueAs(PurrfectPetsJob.class);

        PurrfectPetsJob deactivatedJob = new PurrfectPetsJob();
        deactivatedJob.setId("job-" + purrfectPetsJobIdCounter.getAndIncrement());
        deactivatedJob.setJobId(existingJob.getJobId());
        deactivatedJob.setPetType(existingJob.getPetType());
        deactivatedJob.setAction(existingJob.getAction());
        deactivatedJob.setStatus(PurrfectPetsJob.StatusEnum.FAILED); // or specific DEACTIVATED status if defined
        deactivatedJob.setCreatedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, deactivatedJob);
        UUID technicalId = idFuture.get();

        logger.info("Deactivated PurrfectPetsJob with new technicalId: {}", technicalId);

        return ResponseEntity.ok("Job deactivated");
    }

    // POST /controller/pet/{id}/deactivate - create deactivation event (optional)
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) throws Exception {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
        ArrayNode items = itemsFuture.get();
        if (items == null || items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        ObjectNode objNode = (ObjectNode) items.get(0);
        Pet existingPet = objNode.traverse().readValueAs(Pet.class);

        Pet deactivatedPet = new Pet();
        deactivatedPet.setId("pet-" + petIdCounter.getAndIncrement());
        deactivatedPet.setPetId(existingPet.getPetId());
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setCategory(existingPet.getCategory());
        deactivatedPet.setStatus(existingPet.getStatus());
        deactivatedPet.setPhotoUrls(existingPet.getPhotoUrls());
        deactivatedPet.setLifecycleStatus(Pet.StatusEnum.ARCHIVED); // or specific DEACTIVATED if defined
        deactivatedPet.setCreatedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, deactivatedPet);
        UUID technicalId = idFuture.get();

        logger.info("Deactivated Pet with new technicalId: {}", technicalId);

        return ResponseEntity.ok("Pet deactivated");
    }

}