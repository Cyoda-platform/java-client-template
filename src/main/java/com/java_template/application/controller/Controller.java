package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
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
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    // Local counters for generating unique technicalIds since EntityService returns UUID but original code uses string keys
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // POST /entity/purrfectPetsJob - create a new job
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody Map<String, String> request) {
        try {
            String petStatus = request.get("petStatus");
            if (petStatus == null || petStatus.isBlank()) {
                logger.error("Invalid petStatus in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "petStatus is required and cannot be blank"));
            }

            PurrfectPetsJob job = new PurrfectPetsJob();
            String technicalId = "job-" + purrfectPetsJobIdCounter.getAndIncrement();
            job.setTechnicalId(technicalId);
            job.setPetStatus(petStatus);
            job.setRequestedAt(java.time.Instant.now().toString());
            job.setStatus("PENDING");
            job.setResultSummary("");

            // Add job entity to EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
            UUID technicalUuid;
            try {
                technicalUuid = idFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to add PurrfectPetsJob to EntityService: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to create job"));
            }

            // We keep local technicalId for processing and response to preserve old API behavior
            
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception ex) {
            logger.error("Error creating PurrfectPetsJob: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/purrfectPetsJob/{id} - retrieve job by technicalId
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable("id") String id) {
        try {
            // The original technicalId is a string like "job-1", but EntityService stores UUID keys.
            // So we must find the matching job by technicalId field.
            // Use getItemsByCondition with condition on $.technicalId EQUALS id

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", id));

            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition("PurrfectPetsJob", ENTITY_VERSION, condition, true);
            ArrayNode items = future.get();

            if (items == null || items.size() == 0) {
                logger.error("PurrfectPetsJob not found: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PurrfectPetsJob not found"));
            }

            // Deserialize ObjectNode to PurrfectPetsJob
            ObjectNode node = (ObjectNode) items.get(0);
            PurrfectPetsJob job = node.traverse().readValueAs(PurrfectPetsJob.class);

            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception ex) {
            logger.error("Error retrieving PurrfectPetsJob {}: {}", id, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/pet/{id} - retrieve pet by technicalId
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable("id") String id) {
        try {
            // Similar approach: find pet by technicalId field equals id
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", id));

            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
            ArrayNode items = future.get();

            if (items == null || items.size() == 0) {
                logger.error("Pet not found: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
            }

            ObjectNode node = (ObjectNode) items.get(0);
            Pet pet = node.traverse().readValueAs(Pet.class);

            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception ex) {
            logger.error("Error retrieving Pet {}: {}", id, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}