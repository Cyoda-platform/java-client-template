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
                    return CompletableFuture.completedFuture(
                            ResponseEntity.status(HttpStatus.CREATED)
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
                    return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.CREATED).body(pet));
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
                                return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.CREATED).body(newPet));
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
                    return CompletableFuture.completedFuture(
                            ResponseEntity.status(HttpStatus.CREATED)
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
}