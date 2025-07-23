package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/api")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

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
                    log.info("Created PetIngestionJob with technicalId: {}", techId);
                    return ResponseEntity.status(201).body(job);
                });
    }

    @GetMapping("/jobs/pet-ingestion/{technicalId}")
    public CompletableFuture<ResponseEntity<?>> getPetIngestionJob(@PathVariable String technicalId) throws JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        return entityService.getItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, uuid)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("PetIngestionJob not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(404).body("PetIngestionJob not found");
                    }
                    PetIngestionJob job;
                    try {
                        job = objectMapper.treeToValue(objectNode, PetIngestionJob.class);
                    } catch (JsonProcessingException e) {
                        log.error("Error converting PetIngestionJob ObjectNode to entity", e);
                        throw new RuntimeException(e);
                    }
                    return ResponseEntity.ok(job);
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
                    log.info("Created Pet with technicalId: {}", techId);
                    return ResponseEntity.status(201).body(pet);
                });
    }

    @GetMapping("/pets/{technicalId}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String technicalId) throws JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        return entityService.getItem(PET_MODEL, ENTITY_VERSION, uuid)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("Pet not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(404).body("Pet not found");
                    }
                    Pet pet;
                    try {
                        pet = objectMapper.treeToValue(objectNode, Pet.class);
                    } catch (JsonProcessingException e) {
                        log.error("Error converting Pet ObjectNode to entity", e);
                        throw new RuntimeException(e);
                    }
                    return ResponseEntity.ok(pet);
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
                                log.info("Created AdoptionRequest with technicalId: {}", adoptionTechId);
                                return ResponseEntity.status(201).body(request);
                            });
                });
    }

    @GetMapping("/adoption-requests/{technicalId}")
    public CompletableFuture<ResponseEntity<?>> getAdoptionRequest(@PathVariable String technicalId) throws JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        return entityService.getItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, uuid)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("AdoptionRequest not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(404).body("AdoptionRequest not found");
                    }
                    AdoptionRequest request;
                    try {
                        request = objectMapper.treeToValue(objectNode, AdoptionRequest.class);
                    } catch (JsonProcessingException e) {
                        log.error("Error converting AdoptionRequest ObjectNode to entity", e);
                        throw new RuntimeException(e);
                    }
                    return ResponseEntity.ok(request);
                });
    }
}