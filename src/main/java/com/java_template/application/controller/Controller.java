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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String PET_INGESTION_JOB_MODEL = "PetIngestionJob";
    private static final String PET_MODEL = "Pet";
    private static final String ADOPTION_REQUEST_MODEL = "AdoptionRequest";

    // POST /controller/petIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob petIngestionJob) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (petIngestionJob == null || petIngestionJob.getSource() == null || petIngestionJob.getSource().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing source");
            return ResponseEntity.badRequest().body("Missing required field: source");
        }
        petIngestionJob.setStatus("PENDING");
        petIngestionJob.setCreatedAt(LocalDateTime.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, petIngestionJob);
        UUID technicalId = idFuture.get();
        petIngestionJob.setTechnicalId(technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(petIngestionJob);
    }

    // GET /controller/petIngestionJob/{id}
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID uuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
            }
            PetIngestionJob petIngestionJob = objectMapper.treeToValue(node, PetIngestionJob.class);
            return ResponseEntity.ok(petIngestionJob);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid PetIngestionJob id format");
        }
    }

    // POST /controller/pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getType() == null || pet.getType().isBlank()
                || pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: name, type, status");
        }
        pet.setCreatedAt(LocalDateTime.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    // GET /controller/pet/{id}
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID uuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        }
    }

    // POST /controller/pet/{id}/update
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (petUpdate == null || petUpdate.getName() == null || petUpdate.getName().isBlank()
                || petUpdate.getType() == null || petUpdate.getType().isBlank()
                || petUpdate.getStatus() == null || petUpdate.getStatus().isBlank()) {
            log.error("Invalid Pet update request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: name, type, status");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        }
        // Instead of update, create new Pet version with updated data
        Pet newPetVersion = new Pet();
        newPetVersion.setName(petUpdate.getName());
        newPetVersion.setType(petUpdate.getType());
        newPetVersion.setStatus(petUpdate.getStatus());
        newPetVersion.setCreatedAt(LocalDateTime.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, newPetVersion);
        UUID technicalId = idFuture.get();
        newPetVersion.setTechnicalId(technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(newPetVersion);
    }

    // POST /controller/pet/{id}/deactivate
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        }
        CompletableFuture<ObjectNode> existingPetFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, uuid);
        ObjectNode existingNode = existingPetFuture.get();
        if (existingNode == null || existingNode.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet existingPet = objectMapper.treeToValue(existingNode, Pet.class);

        Pet deactivatedPet = new Pet();
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setType(existingPet.getType());
        deactivatedPet.setStatus("DEACTIVATED");
        deactivatedPet.setCreatedAt(LocalDateTime.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, deactivatedPet);
        UUID technicalId = idFuture.get();
        deactivatedPet.setTechnicalId(technicalId);

        log.info("Pet with original technicalId {} deactivated by creating new entity with technicalId {}", id, technicalId.toString());

        return ResponseEntity.ok("Pet deactivated successfully");
    }

    // POST /controller/adoptionRequest
    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (adoptionRequest == null || adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isBlank()
                || adoptionRequest.getAdopterName() == null || adoptionRequest.getAdopterName().isBlank()
                || adoptionRequest.getStatus() == null || adoptionRequest.getStatus().isBlank()) {
            log.error("Invalid AdoptionRequest creation request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: petId, adopterName, status");
        }
        UUID petUuid;
        try {
            petUuid = UUID.fromString(adoptionRequest.getPetId());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        }
        CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, petUuid);
        ObjectNode petNode = petNodeFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced pet does not exist");
        }
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet is not available for adoption");
        }
        adoptionRequest.setStatus("SUBMITTED");
        adoptionRequest.setCreatedAt(LocalDateTime.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, adoptionRequest);
        UUID technicalId = idFuture.get();
        adoptionRequest.setTechnicalId(technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(adoptionRequest);
    }

    // GET /controller/adoptionRequest/{id}
    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID uuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
            }
            AdoptionRequest adoptionRequest = objectMapper.treeToValue(node, AdoptionRequest.class);
            return ResponseEntity.ok(adoptionRequest);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid AdoptionRequest id format");
        }
    }
}