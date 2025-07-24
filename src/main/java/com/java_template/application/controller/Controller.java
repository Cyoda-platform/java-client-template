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

            processPurrfectPetsJob(technicalId.toString());

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

    // processPurrfectPetsJob - main business logic for job processing
    private void processPurrfectPetsJob(String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_PURRECT_PETS_JOB,
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            );
            ObjectNode jobNode = itemFuture.join();
            if (jobNode == null || jobNode.isEmpty()) {
                log.error("Job not found during processing with ID: {}", technicalId);
                return;
            }
            String status = jobNode.get("status").asText();
            if (!"PENDING".equals(status)) {
                log.warn("Job with ID {} is not in PENDING status, skipping processing", technicalId);
                return;
            }

            log.info("Processing PurrfectPetsJob with ID: {}", technicalId);

            // Update status to PROCESSING by creating new version
            ObjectNode processingJobNode = jobNode.deepCopy();
            processingJobNode.put("status", "PROCESSING");

            // Add new entity version for PROCESSING status
            CompletableFuture<UUID> processingIdFuture = entityService.addItem(
                    ENTITY_PURRECT_PETS_JOB,
                    ENTITY_VERSION,
                    processingJobNode
            );
            UUID processingTechnicalId = processingIdFuture.join();

            String action = null;
            if (jobNode.has("requestedAction") && !jobNode.get("requestedAction").isNull()) {
                action = jobNode.get("requestedAction").asText();
            }
            if (action == null || action.isBlank()) {
                log.error("requestedAction is null or blank");
                // Update to FAILED
                ObjectNode failedJobNode = processingJobNode.deepCopy();
                failedJobNode.put("status", "FAILED");
                entityService.addItem(ENTITY_PURRECT_PETS_JOB, ENTITY_VERSION, failedJobNode).join();
                return;
            }
            String actionUpper = action.toUpperCase(Locale.ROOT);

            if ("LOAD_PETS".equals(actionUpper)) {
                loadPetsAndSave();
                // Update to COMPLETED
                ObjectNode completedJobNode = processingJobNode.deepCopy();
                completedJobNode.put("status", "COMPLETED");
                entityService.addItem(ENTITY_PURRECT_PETS_JOB, ENTITY_VERSION, completedJobNode).join();
                log.info("Completed LOAD_PETS action for job ID: {}", technicalId);
            } else if ("SAVE_PET".equals(actionUpper)) {
                log.info("SAVE_PET action requested but no pet data in job - skipping actual save");
                // Update to COMPLETED
                ObjectNode completedJobNode = processingJobNode.deepCopy();
                completedJobNode.put("status", "COMPLETED");
                entityService.addItem(ENTITY_PURRECT_PETS_JOB, ENTITY_VERSION, completedJobNode).join();
            } else {
                log.error("Unknown requestedAction: {}", action);
                // Update to FAILED
                ObjectNode failedJobNode = processingJobNode.deepCopy();
                failedJobNode.put("status", "FAILED");
                entityService.addItem(ENTITY_PURRECT_PETS_JOB, ENTITY_VERSION, failedJobNode).join();
            }
        } catch (Exception ex) {
            log.error("Exception processing PurrfectPetsJob ID {}: {}", technicalId, ex.getMessage());
            try {
                CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                        ENTITY_PURRECT_PETS_JOB,
                        ENTITY_VERSION,
                        UUID.fromString(technicalId)
                );
                ObjectNode jobNode = itemFuture.join();
                if (jobNode != null && !jobNode.isEmpty()) {
                    ObjectNode failedJobNode = jobNode.deepCopy();
                    failedJobNode.put("status", "FAILED");
                    entityService.addItem(ENTITY_PURRECT_PETS_JOB, ENTITY_VERSION, failedJobNode).join();
                }
            } catch (Exception e) {
                log.error("Error updating job status to FAILED for ID {}: {}", technicalId, e.getMessage());
            }
        }
    }

    // loadPetsAndSave - fetch pets from external Petstore API and save immutably
    private void loadPetsAndSave() {
        try {
            // Perform external call to Petstore API
            var restTemplate = new org.springframework.web.client.RestTemplate();
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<Pet[]> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Pet[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Pet[] pets = response.getBody();
                for (Pet pet : pets) {
                    savePetImmutable(pet);
                }
                log.info("Loaded and saved {} pets from Petstore API", pets.length);
            } else {
                log.error("Failed to load pets from Petstore API, status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Exception loading pets from Petstore API: {}", e.getMessage());
        }
    }

    // savePetImmutable - save pet by creating new entity, then process pet
    private void savePetImmutable(Pet pet) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_PET,
                    ENTITY_VERSION,
                    pet
            );
            UUID technicalId = idFuture.join();

            processPet(technicalId.toString());

        } catch (Exception e) {
            log.error("Error saving pet immutably: {}", e.getMessage());
        }
    }

    // processPet - business logic for pet processing
    private void processPet(String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_PET,
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            );
            ObjectNode petNode = itemFuture.join();
            if (petNode == null || petNode.isEmpty()) {
                log.error("Pet not found during processing with ID: {}", technicalId);
                return;
            }
            Pet pet = new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(petNode, Pet.class);
            if (!pet.isValid()) {
                log.error("Invalid pet data for ID: {}", technicalId);
                return;
            }
            if (pet.getTags() == null) {
                pet.setTags(new ArrayList<>());
            }
            if (!pet.getTags().contains("Purrfect")) {
                pet.getTags().add("Purrfect");
            }
            // Save updated pet as new version
            entityService.addItem(
                    ENTITY_PET,
                    ENTITY_VERSION,
                    pet
            ).join();
            log.info("Processed Pet with technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing pet with ID {}: {}", technicalId, e.getMessage());
        }
    }
}