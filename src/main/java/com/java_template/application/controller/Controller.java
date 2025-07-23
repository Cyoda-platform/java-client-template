package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/api")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PET_INGESTION_JOB_MODEL = "PetIngestionJob";
    private static final String PET_MODEL = "Pet";
    private static final String ADOPTION_REQUEST_MODEL = "AdoptionRequest";

    // -------- PetIngestionJob Endpoints --------

    @PostMapping("/jobs/pet-ingestion")
    public CompletableFuture<ResponseEntity<?>> createPetIngestionJob(@RequestBody PetIngestionJob job) {
        if (job == null || job.getSource() == null || job.getSource().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing source");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Source is required"));
        }
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus("PENDING");

        return entityService.addItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, job)
                .thenApply(techId -> {
                    job.setTechnicalId(techId);
                    processPetIngestionJob(job);
                    log.info("Created PetIngestionJob with technicalId: {}", techId);
                    return ResponseEntity.status(201).body(job);
                });
    }

    @GetMapping("/jobs/pet-ingestion/{technicalId}")
    public CompletableFuture<ResponseEntity<?>> getPetIngestionJob(@PathVariable UUID technicalId) {
        return entityService.getItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("PetIngestionJob not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(404).body("PetIngestionJob not found");
                    }
                    return ResponseEntity.ok(objectNode);
                });
    }

    // -------- Pet Endpoints --------

    @PostMapping("/pets")
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet creation request");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid pet data"));
        }
        pet.setStatus("NEW");

        return entityService.addItem(PET_MODEL, ENTITY_VERSION, pet)
                .thenApply(techId -> {
                    pet.setTechnicalId(techId);
                    processPet(pet);
                    log.info("Created Pet with technicalId: {}", techId);
                    return ResponseEntity.status(201).body(pet);
                });
    }

    @GetMapping("/pets/{technicalId}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable UUID technicalId) {
        return entityService.getItem(PET_MODEL, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("Pet not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(404).body("Pet not found");
                    }
                    return ResponseEntity.ok(objectNode);
                });
    }

    // -------- AdoptionRequest Endpoints --------

    @PostMapping("/adoption-requests")
    public CompletableFuture<ResponseEntity<?>> createAdoptionRequest(@RequestBody AdoptionRequest request) {
        if (request == null || !request.isValid()) {
            log.error("Invalid AdoptionRequest creation request");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid adoption request data"));
        }

        // Verify referenced Pet exists and is AVAILABLE
        UUID petTechId;
        try {
            petTechId = UUID.fromString(request.getPetId());
        } catch (Exception e) {
            log.error("Invalid Pet technicalId format: {}", request.getPetId());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid Pet ID format"));
        }

        return entityService.getItem(PET_MODEL, ENTITY_VERSION, petTechId)
                .thenCompose(petObjectNode -> {
                    if (petObjectNode == null || petObjectNode.isEmpty()) {
                        log.error("Referenced Pet not found with technicalId: {}", request.getPetId());
                        return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Referenced Pet does not exist"));
                    }
                    String status = petObjectNode.path("status").asText(null);
                    if (!"AVAILABLE".equalsIgnoreCase(status)) {
                        log.error("Pet with technicalId {} is not available for adoption", request.getPetId());
                        return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Pet is not available for adoption"));
                    }

                    request.setRequestDate(LocalDateTime.now());
                    request.setStatus("PENDING");

                    return entityService.addItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, request)
                            .thenApply(adoptionTechId -> {
                                request.setTechnicalId(adoptionTechId);
                                processAdoptionRequest(request, petObjectNode, petTechId);
                                log.info("Created AdoptionRequest with technicalId: {}", adoptionTechId);
                                return ResponseEntity.status(201).body(request);
                            });
                });
    }

    @GetMapping("/adoption-requests/{technicalId}")
    public CompletableFuture<ResponseEntity<?>> getAdoptionRequest(@PathVariable UUID technicalId) {
        return entityService.getItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("AdoptionRequest not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(404).body("AdoptionRequest not found");
                    }
                    return ResponseEntity.ok(objectNode);
                });
    }

    // -------- Process Methods --------

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());

        if (job.getSource() == null || job.getSource().isBlank()) {
            log.error("PetIngestionJob {} has invalid source", job.getTechnicalId());
            job.setStatus("FAILED");
            entityService.updateItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, job.getTechnicalId(), job);
            return;
        }

        job.setStatus("PROCESSING");
        entityService.updateItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, job.getTechnicalId(), job)
                .thenAccept(updatedId -> {
                    try {
                        // Simulate fetching pets from external Petstore API
                        Pet newPet = new Pet();
                        newPet.setName("Sample Pet");
                        newPet.setCategory("cat");
                        newPet.setBreed("Siamese");
                        newPet.setAge(1);
                        newPet.setStatus("NEW");

                        entityService.addItem(PET_MODEL, ENTITY_VERSION, newPet)
                                .thenAccept(petTechId -> {
                                    newPet.setTechnicalId(petTechId);
                                    processPet(newPet);
                                    job.setStatus("COMPLETED");
                                    entityService.updateItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, job.getTechnicalId(), job);
                                    log.info("PetIngestionJob {} completed successfully", job.getTechnicalId());
                                }).exceptionally(e -> {
                                    job.setStatus("FAILED");
                                    entityService.updateItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, job.getTechnicalId(), job);
                                    log.error("PetIngestionJob {} failed adding pet: {}", job.getTechnicalId(), e.getMessage());
                                    return null;
                                });
                    } catch (Exception e) {
                        job.setStatus("FAILED");
                        entityService.updateItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, job.getTechnicalId(), job);
                        log.error("PetIngestionJob {} failed processing: {}", job.getTechnicalId(), e.getMessage());
                    }
                }).exceptionally(e -> {
                    log.error("PetIngestionJob {} update failed: {}", job.getTechnicalId(), e.getMessage());
                    return null;
                });
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getName() == null || pet.getName().isBlank() ||
                pet.getCategory() == null || pet.getCategory().isBlank() ||
                pet.getBreed() == null || pet.getBreed().isBlank() ||
                pet.getAge() == null) {
            log.error("Pet {} has invalid fields", pet.getTechnicalId());
            return;
        }

        pet.setStatus("AVAILABLE");
        entityService.updateItem(PET_MODEL, ENTITY_VERSION, pet.getTechnicalId(), pet)
                .thenAccept(updatedId -> log.info("Pet {} is now AVAILABLE", pet.getTechnicalId()))
                .exceptionally(e -> {
                    log.error("Failed to update Pet {} status: {}", pet.getTechnicalId(), e.getMessage());
                    return null;
                });
    }

    private void processAdoptionRequest(AdoptionRequest request, ObjectNode petObjectNode, UUID petTechId) {
        log.info("Processing AdoptionRequest with technicalId: {}", request.getTechnicalId());

        if (petObjectNode == null || petObjectNode.isEmpty()) {
            log.error("AdoptionRequest {} references unknown Pet technicalId: {}", request.getTechnicalId(), request.getPetId());
            request.setStatus("REJECTED");
            entityService.updateItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, request.getTechnicalId(), request);
            return;
        }

        String petStatus = petObjectNode.path("status").asText(null);
        if (!"AVAILABLE".equalsIgnoreCase(petStatus)) {
            log.error("AdoptionRequest {} rejected because Pet {} status is {}", request.getTechnicalId(), request.getPetId(), petStatus);
            request.setStatus("REJECTED");
            entityService.updateItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, request.getTechnicalId(), request);
            return;
        }

        if (request.getRequesterName() != null && !request.getRequesterName().isBlank()) {
            request.setStatus("APPROVED");
            // Update pet status to ADOPTED
            Pet pet = new Pet();
            pet.setTechnicalId(petTechId);
            pet.setStatus("ADOPTED");
            entityService.updateItem(PET_MODEL, ENTITY_VERSION, petTechId, pet)
                    .thenAccept(updatedId -> log.info("Pet {} adopted", petTechId))
                    .exceptionally(e -> {
                        log.error("Failed to update Pet {} to ADOPTED: {}", petTechId, e.getMessage());
                        return null;
                    });
            log.info("AdoptionRequest {} approved, Pet {} adopted", request.getTechnicalId(), petTechId);
        } else {
            request.setStatus("REJECTED");
            log.info("AdoptionRequest {} rejected due to invalid requester name", request.getTechnicalId());
        }
        entityService.updateItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, request.getTechnicalId(), request)
                .exceptionally(e -> {
                    log.error("Failed to update AdoptionRequest {} status: {}", request.getTechnicalId(), e.getMessage());
                    return null;
                });
    }
}