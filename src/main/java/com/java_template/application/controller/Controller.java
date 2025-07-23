package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetAdoptionTask;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PETJOB_ENTITY = "PetJob";
    private static final String PET_ENTITY = "Pet";
    private static final String PETADOPTIONTASK_ENTITY = "PetAdoptionTask";

    // POST /controller/petjobs - create PetJob event
    @PostMapping("/petjobs")
    public CompletableFuture<ResponseEntity<?>> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || !petJob.isValid()) {
            log.error("Invalid PetJob creation request");
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data."));
        }
        petJob.setStatus("PENDING");
        petJob.setCreatedAt(LocalDateTime.now());

        return entityService.addItem(PETJOB_ENTITY, ENTITY_VERSION, petJob)
                .thenCompose(technicalId -> {
                    petJob.setTechnicalId(technicalId);
                    return processPetJob(petJob)
                            .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED)
                                    .body(Map.of("jobId", technicalId.toString(), "status", petJob.getStatus())));
                })
                .exceptionally(ex -> {
                    log.error("Error processing PetJob: {}", ex.getMessage());
                    petJob.setStatus("FAILED");
                    entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process PetJob.");
                });
    }

    // GET /controller/petjobs/{id} - retrieve PetJob by id
    @GetMapping("/petjobs/{id}")
    public CompletableFuture<ResponseEntity<?>> getPetJob(@PathVariable String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob id format."));
        }
        return entityService.getItem(PETJOB_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found.");
                    }
                    return ResponseEntity.ok(item);
                });
    }

    // POST /controller/pets - create Pet event (immutable)
    @PostMapping("/pets")
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet creation request");
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data."));
        }
        pet.setStatus("ACTIVE");

        return entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet)
                .thenCompose(technicalId -> {
                    pet.setTechnicalId(technicalId);
                    return processPet(pet)
                            .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED).body(pet));
                })
                .exceptionally(ex -> {
                    log.error("Error processing Pet: {}", ex.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process Pet.");
                });
    }

    // GET /controller/pets/{id} - retrieve Pet by id
    @GetMapping("/pets/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format."));
        }
        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found.");
                    }
                    return ResponseEntity.ok(item);
                });
    }

    // POST /controller/pets/{id}/update - create new Pet version (immutable)
    @PostMapping("/pets/{id}/update")
    public CompletableFuture<ResponseEntity<?>> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) {
        if (petUpdate == null || !petUpdate.isValid()) {
            log.error("Invalid Pet update request");
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet update data."));
        }
        UUID originalTechnicalId;
        try {
            originalTechnicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format."));
        }

        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, originalTechnicalId)
                .thenCompose(existingNode -> {
                    if (existingNode == null || existingNode.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found."));
                    }
                    Pet existingPet = petUpdate; // We'll create a new Pet instance with copied fields below
                    // Extract existingPet fields from ObjectNode
                    try {
                        existingPet = new Pet();
                        existingPet.setTechnicalId(originalTechnicalId);
                        existingPet.setId(existingNode.get("id").asText(""));
                        existingPet.setName(existingNode.get("name").asText(null));
                        existingPet.setSpecies(existingNode.get("species").asText(null));
                        existingPet.setBreed(existingNode.get("breed").asText(null));
                        existingPet.setAge(existingNode.has("age") ? existingNode.get("age").asInt() : 0);
                        existingPet.setAdoptionStatus(existingNode.get("adoptionStatus").asText(null));
                        existingPet.setStatus(existingNode.get("status").asText(null));
                    } catch (Exception e) {
                        log.error("Failed to parse existing Pet fields: {}", e.getMessage());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to parse existing Pet."));
                    }
                    // Prepare new Pet version
                    Pet newPet = petUpdate;
                    newPet.setStatus(existingPet.getStatus()); // preserve unless changed explicitly
                    // Add new Pet version
                    return entityService.addItem(PET_ENTITY, ENTITY_VERSION, newPet)
                            .thenCompose(newTechnicalId -> {
                                newPet.setTechnicalId(newTechnicalId);
                                return processPet(newPet)
                                        .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED).body(newPet));
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error processing Pet update: {}", ex.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process Pet update.");
                });
    }

    // POST /controller/pets/{id}/deactivate - create deactivation record (immutable)
    @PostMapping("/pets/{id}/deactivate")
    public CompletableFuture<ResponseEntity<?>> deactivatePet(@PathVariable String id) {
        UUID originalTechnicalId;
        try {
            originalTechnicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet id format."));
        }

        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, originalTechnicalId)
                .thenCompose(existingNode -> {
                    if (existingNode == null || existingNode.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found."));
                    }
                    Pet existingPet = new Pet();
                    try {
                        existingPet.setTechnicalId(originalTechnicalId);
                        existingPet.setId(existingNode.get("id").asText(null));
                        existingPet.setName(existingNode.get("name").asText(null));
                        existingPet.setSpecies(existingNode.get("species").asText(null));
                        existingPet.setBreed(existingNode.get("breed").asText(null));
                        existingPet.setAge(existingNode.has("age") ? existingNode.get("age").asInt() : 0);
                        existingPet.setAdoptionStatus(existingNode.get("adoptionStatus").asText(null));
                        existingPet.setStatus(existingNode.get("status").asText(null));
                    } catch (Exception e) {
                        log.error("Failed to parse existing Pet fields: {}", e.getMessage());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to parse existing Pet."));
                    }
                    Pet deactivatedPet = new Pet();
                    deactivatedPet.setName(existingPet.getName());
                    deactivatedPet.setSpecies(existingPet.getSpecies());
                    deactivatedPet.setBreed(existingPet.getBreed());
                    deactivatedPet.setAge(existingPet.getAge());
                    deactivatedPet.setAdoptionStatus(existingPet.getAdoptionStatus());
                    deactivatedPet.setStatus("INACTIVE");

                    return entityService.addItem(PET_ENTITY, ENTITY_VERSION, deactivatedPet)
                            .thenApply(newTechnicalId -> {
                                deactivatedPet.setTechnicalId(newTechnicalId);
                                log.info("Pet with technicalId {} deactivated, new version technicalId {}", id, newTechnicalId);
                                return ResponseEntity.ok(Map.of("message", "Pet deactivated", "newId", newTechnicalId.toString()));
                            });
                });
    }

    // POST /controller/petadoptiontasks - create PetAdoptionTask event
    @PostMapping("/petadoptiontasks")
    public CompletableFuture<ResponseEntity<?>> createPetAdoptionTask(@RequestBody PetAdoptionTask task) {
        if (task == null || !task.isValid()) {
            log.error("Invalid PetAdoptionTask creation request");
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetAdoptionTask data."));
        }
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());

        return entityService.addItem(PETADOPTIONTASK_ENTITY, ENTITY_VERSION, task)
                .thenCompose(technicalId -> {
                    task.setTechnicalId(technicalId);
                    return processPetAdoptionTask(task)
                            .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED)
                                    .body(Map.of("taskId", technicalId.toString(), "status", task.getStatus())));
                })
                .exceptionally(ex -> {
                    log.error("Error processing PetAdoptionTask: {}", ex.getMessage());
                    task.setStatus("FAILED");
                    entityService.updateItem(PETADOPTIONTASK_ENTITY, ENTITY_VERSION, task.getTechnicalId(), task);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process PetAdoptionTask.");
                });
    }

    // GET /controller/petadoptiontasks/{id} - retrieve PetAdoptionTask by id
    @GetMapping("/petadoptiontasks/{id}")
    public CompletableFuture<ResponseEntity<?>> getPetAdoptionTask(@PathVariable String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetAdoptionTask id format."));
        }
        return entityService.getItem(PETADOPTIONTASK_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetAdoptionTask not found.");
                    }
                    return ResponseEntity.ok(item);
                });
    }

    // Business logic for PetJob processing
    private CompletableFuture<Void> processPetJob(PetJob petJob) {
        log.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());
        petJob.setStatus("PROCESSING");
        return CompletableFuture.runAsync(() -> {
            if (petJob.getPetId() == null || petJob.getPetId().isBlank()) {
                log.error("PetJob {} validation failed: petId is blank", petJob.getTechnicalId());
                petJob.setStatus("FAILED");
                entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                return;
            }
            if (petJob.getAction() == null || petJob.getAction().isBlank()) {
                log.error("PetJob {} validation failed: action is blank", petJob.getTechnicalId());
                petJob.setStatus("FAILED");
                entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                return;
            }

            try {
                switch (petJob.getAction()) {
                    case "CREATE":
                        Pet newPet = new Pet();
                        newPet.setName("New Pet"); // placeholder; in real case should come from input or external source
                        newPet.setSpecies("Unknown");
                        newPet.setBreed("Unknown");
                        newPet.setAge(0);
                        newPet.setAdoptionStatus("AVAILABLE");
                        newPet.setStatus("ACTIVE");
                        entityService.addItem(PET_ENTITY, ENTITY_VERSION, newPet).join();
                        log.info("Created new Pet from PetJob {}", petJob.getTechnicalId());
                        break;
                    case "UPDATE":
                        UUID petTechnicalId;
                        try {
                            petTechnicalId = UUID.fromString(petJob.getPetId());
                        } catch (IllegalArgumentException e) {
                            log.error("PetJob {} update failed: Invalid petId format", petJob.getTechnicalId());
                            petJob.setStatus("FAILED");
                            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                            return;
                        }
                        ObjectNode existingPetNode = entityService.getItem(PET_ENTITY, ENTITY_VERSION, petTechnicalId).join();
                        if (existingPetNode == null || existingPetNode.isEmpty()) {
                            log.error("PetJob {} update failed: Pet not found", petJob.getTechnicalId());
                            petJob.setStatus("FAILED");
                            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                            return;
                        }
                        Pet updatedPet = new Pet();
                        updatedPet.setName(existingPetNode.get("name").asText(null));
                        updatedPet.setSpecies(existingPetNode.get("species").asText(null));
                        updatedPet.setBreed(existingPetNode.get("breed").asText(null));
                        updatedPet.setAge(existingPetNode.has("age") ? existingPetNode.get("age").asInt() : 0);
                        updatedPet.setAdoptionStatus(existingPetNode.get("adoptionStatus").asText(null));
                        updatedPet.setStatus(existingPetNode.get("status").asText(null));
                        entityService.addItem(PET_ENTITY, ENTITY_VERSION, updatedPet).join();
                        log.info("Created updated Pet version from PetJob {}", petJob.getTechnicalId());
                        break;
                    case "STATUS_CHANGE":
                        try {
                            petTechnicalId = UUID.fromString(petJob.getPetId());
                        } catch (IllegalArgumentException e) {
                            log.error("PetJob {} status change failed: Invalid petId format", petJob.getTechnicalId());
                            petJob.setStatus("FAILED");
                            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                            return;
                        }
                        ObjectNode petToChangeNode = entityService.getItem(PET_ENTITY, ENTITY_VERSION, petTechnicalId).join();
                        if (petToChangeNode == null || petToChangeNode.isEmpty()) {
                            log.error("PetJob {} status change failed: Pet not found", petJob.getTechnicalId());
                            petJob.setStatus("FAILED");
                            entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                            return;
                        }
                        Pet statusChangedPet = new Pet();
                        statusChangedPet.setName(petToChangeNode.get("name").asText(null));
                        statusChangedPet.setSpecies(petToChangeNode.get("species").asText(null));
                        statusChangedPet.setBreed(petToChangeNode.get("breed").asText(null));
                        statusChangedPet.setAge(petToChangeNode.has("age") ? petToChangeNode.get("age").asInt() : 0);
                        statusChangedPet.setAdoptionStatus("ADOPTED"); // example status change
                        statusChangedPet.setStatus(petToChangeNode.get("status").asText(null));
                        entityService.addItem(PET_ENTITY, ENTITY_VERSION, statusChangedPet).join();
                        log.info("Created Pet status changed version from PetJob {}", petJob.getTechnicalId());
                        break;
                    default:
                        log.error("PetJob {} unknown action: {}", petJob.getTechnicalId(), petJob.getAction());
                        petJob.setStatus("FAILED");
                        entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
                        return;
                }
                petJob.setStatus("COMPLETED");
                entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
            } catch (Exception e) {
                log.error("PetJob {} processing error: {}", petJob.getTechnicalId(), e.getMessage());
                petJob.setStatus("FAILED");
                entityService.updateItem(PETJOB_ENTITY, ENTITY_VERSION, petJob.getTechnicalId(), petJob);
            }
        });
    }

    // Business logic for PetAdoptionTask processing
    private CompletableFuture<Void> processPetAdoptionTask(PetAdoptionTask task) {
        log.info("Processing PetAdoptionTask with technicalId: {}", task.getTechnicalId());
        task.setStatus("PROCESSING");
        return CompletableFuture.runAsync(() -> {
            if (task.getPetId() == null || task.getPetId().isBlank()) {
                log.error("PetAdoptionTask {} validation failed: petId is blank", task.getTechnicalId());
                task.setStatus("FAILED");
                entityService.updateItem(PETADOPTIONTASK_ENTITY, ENTITY_VERSION, task.getTechnicalId(), task);
                return;
            }
            if (task.getTaskType() == null || task.getTaskType().isBlank()) {
                log.error("PetAdoptionTask {} validation failed: taskType is blank", task.getTechnicalId());
                task.setStatus("FAILED");
                entityService.updateItem(PETADOPTIONTASK_ENTITY, ENTITY_VERSION, task.getTechnicalId(), task);
                return;
            }

            try {
                log.info("PetAdoptionTask {} processing task type: {}", task.getTechnicalId(), task.getTaskType());
                // Simulate processing delay or external calls here if needed
                task.setStatus("COMPLETED");
                entityService.updateItem(PETADOPTIONTASK_ENTITY, ENTITY_VERSION, task.getTechnicalId(), task);
            } catch (Exception e) {
                log.error("PetAdoptionTask {} processing error: {}", task.getTechnicalId(), e.getMessage());
                task.setStatus("FAILED");
                entityService.updateItem(PETADOPTIONTASK_ENTITY, ENTITY_VERSION, task.getTechnicalId(), task);
            }
        });
    }

    // Business logic for Pet processing
    private CompletableFuture<Void> processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        return CompletableFuture.runAsync(() -> {
            // Validate pet fields - already done in isValid()
            // Enrich pet data if necessary
            // External API calls can be simulated here if required
            log.info("Pet {} processed successfully", pet.getTechnicalId());
        });
    }
}