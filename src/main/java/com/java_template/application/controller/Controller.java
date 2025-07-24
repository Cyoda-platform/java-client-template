package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.Valid;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    // POST /entity/purrfectPetsJob - create a new job
    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@Valid @RequestBody Map<String, String> request) {
        try {
            String petStatus = request.get("petStatus");
            if (petStatus == null || petStatus.isBlank()) {
                logger.error("Invalid petStatus in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "petStatus is required and cannot be blank"));
            }

            PurrfectPetsJob job = new PurrfectPetsJob();
            // PurrfectPetsJob.technicalId is a String field, but EntityService expects UUID technicalId for keys
            // Generate UUID for technicalId
            UUID technicalUuid = UUID.randomUUID();
            job.setTechnicalId(technicalUuid.toString());
            job.setPetStatus(petStatus);
            job.setRequestedAt(java.time.Instant.now().toString());
            job.setStatus("PENDING");
            job.setResultSummary("");

            // Add job entity to EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
            UUID returnedUuid;
            try {
                returnedUuid = idFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to add PurrfectPetsJob to EntityService: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to create job"));
            }

            // Return the String representation of UUID as technicalId in response
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", returnedUuid.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception ex) {
            logger.error("Error creating PurrfectPetsJob: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/purrfectPetsJob/{id} - retrieve job by technicalId UUID string
    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable("id") String id) throws JsonProcessingException {
        try {
            UUID technicalUuid = UUID.fromString(id);
            ObjectNode node = entityService.getItem("PurrfectPetsJob", ENTITY_VERSION, technicalUuid).get();
            if (node == null) {
                logger.error("PurrfectPetsJob not found: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PurrfectPetsJob not found"));
            }
            PurrfectPetsJob job = objectMapper.treeToValue(node, PurrfectPetsJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID argument: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving PurrfectPetsJob {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/pet/{id} - retrieve pet by technicalId UUID string
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable("id") String id) throws JsonProcessingException {
        try {
            UUID technicalUuid = UUID.fromString(id);
            ObjectNode node = entityService.getItem("Pet", ENTITY_VERSION, technicalUuid).get();
            if (node == null) {
                logger.error("Pet not found: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
            }
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID argument: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving Pet {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}