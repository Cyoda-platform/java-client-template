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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    private static final String ENTITY_PURRECT_PETS_JOB = "PurrfectPetsJob";
    private static final String ENTITY_PET = "Pet";

    // POST /api/jobs - create a new PurrfectPetsJob and trigger processing
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody PurrfectPetsJob job) {
        try {
            if (job == null) {
                log.error("Received null job");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Job payload must not be null"));
            }
            if (!job.isValid()) {
                log.error("Invalid job received: {}", job);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid job data"));
            }

            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_PURRECT_PETS_JOB,
                    ENTITY_VERSION,
                    job
            );
            UUID technicalId = idFuture.join();

            // processPurrfectPetsJob removed

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument error in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/jobs/{id} - retrieve job by technicalId
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJob(@PathVariable String id) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_PURRECT_PETS_JOB,
                    ENTITY_VERSION,
                    UUID.fromString(id)
            );
            ObjectNode jobNode = itemFuture.join();
            if (jobNode == null || jobNode.isEmpty()) {
                log.error("Job not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }
            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument error in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/pets/{id} - retrieve pet by technicalId
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_PET,
                    ENTITY_VERSION,
                    UUID.fromString(id)
            );
            ObjectNode petNode = itemFuture.join();
            if (petNode == null || petNode.isEmpty()) {
                log.error("Pet not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
            }
            return ResponseEntity.ok(petNode);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument error in getPet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error in getPet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

}