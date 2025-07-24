package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PET_INGESTION_JOB_MODEL = "PetIngestionJob";
    private static final String PET_MODEL = "Pet";
    private static final String ADOPTION_REQUEST_MODEL = "AdoptionRequest";

    // POST /controller/petIngestionJob
    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob petIngestionJob) {
        if (petIngestionJob == null || petIngestionJob.getSource() == null || petIngestionJob.getSource().isBlank()) {
            log.error("Invalid PetIngestionJob creation request: missing source");
            return ResponseEntity.badRequest().body("Missing required field: source");
        }
        petIngestionJob.setStatus("PENDING");
        petIngestionJob.setCreatedAt(LocalDateTime.now());
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, petIngestionJob);
            UUID technicalId = idFuture.get();
            // We can set technicalId as id (string form) if needed, but original code used string IDs, here we keep technicalId usage internally
            // Processing asynchronously / after creation:
            processPetIngestionJob(petIngestionJob);
            return ResponseEntity.status(HttpStatus.CREATED).body(petIngestionJob);
        } catch (Exception e) {
            log.error("Failed to create PetIngestionJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create PetIngestionJob");
        }
    }

    // GET /controller/petIngestionJob/{id}
    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_INGESTION_JOB_MODEL, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid PetIngestionJob id format");
        } catch (Exception e) {
            log.error("Failed to get PetIngestionJob {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get PetIngestionJob");
        }
    }

    // POST /controller/pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || pet.getName() == null || pet.getName().isBlank()
                || pet.getType() == null || pet.getType().isBlank()
                || pet.getStatus() == null || pet.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: name, type, status");
        }
        pet.setCreatedAt(LocalDateTime.now());
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, pet);
            UUID technicalId = idFuture.get();
            // processPet expects Pet object, so call directly
            processPet(pet);
            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Failed to create Pet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create Pet");
        }
    }

    // GET /controller/pet/{id}
    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        } catch (Exception e) {
            log.error("Failed to get Pet {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get Pet");
        }
    }

    // POST /controller/pet/{id}/update
    @PostMapping("/pet/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) {
        if (petUpdate == null || petUpdate.getName() == null || petUpdate.getName().isBlank()
                || petUpdate.getType() == null || petUpdate.getType().isBlank()
                || petUpdate.getStatus() == null || petUpdate.getStatus().isBlank()) {
            log.error("Invalid Pet update request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: name, type, status");
        }
        try {
            UUID uuid = UUID.fromString(id);
            // Instead of update, create new Pet version with updated data
            Pet newPetVersion = new Pet();
            newPetVersion.setName(petUpdate.getName());
            newPetVersion.setType(petUpdate.getType());
            newPetVersion.setStatus(petUpdate.getStatus());
            newPetVersion.setCreatedAt(LocalDateTime.now());
            CompletableFuture<UUID> idFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, newPetVersion);
            UUID technicalId = idFuture.get();
            processPet(newPetVersion);
            return ResponseEntity.status(HttpStatus.CREATED).body(newPetVersion);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        } catch (Exception e) {
            log.error("Failed to update Pet {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update Pet");
        }
    }

    // POST /controller/pet/{id}/deactivate
    @PostMapping("/pet/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> existingPetFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, uuid);
            ObjectNode existingNode = existingPetFuture.get();
            if (existingNode == null || existingNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            Pet existingPet = entityService.getObjectMapper().treeToValue(existingNode, Pet.class);

            Pet deactivatedPet = new Pet();
            deactivatedPet.setName(existingPet.getName());
            deactivatedPet.setType(existingPet.getType());
            deactivatedPet.setStatus("DEACTIVATED");
            deactivatedPet.setCreatedAt(LocalDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, deactivatedPet);
            UUID technicalId = idFuture.get();

            log.info("Pet with original technicalId {} deactivated by creating new entity with technicalId {}", id, technicalId.toString());

            return ResponseEntity.ok("Pet deactivated successfully");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        } catch (Exception e) {
            log.error("Failed to deactivate Pet {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to deactivate Pet");
        }
    }

    // POST /controller/adoptionRequest
    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) {
        if (adoptionRequest == null || adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isBlank()
                || adoptionRequest.getAdopterName() == null || adoptionRequest.getAdopterName().isBlank()
                || adoptionRequest.getStatus() == null || adoptionRequest.getStatus().isBlank()) {
            log.error("Invalid AdoptionRequest creation request: missing required fields");
            return ResponseEntity.badRequest().body("Missing required fields: petId, adopterName, status");
        }
        try {
            UUID petUuid = UUID.fromString(adoptionRequest.getPetId());
            CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, petUuid);
            ObjectNode petNode = petNodeFuture.get();
            if (petNode == null || petNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced pet does not exist");
            }
            Pet pet = entityService.getObjectMapper().treeToValue(petNode, Pet.class);
            if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet is not available for adoption");
            }
            adoptionRequest.setStatus("SUBMITTED");
            adoptionRequest.setCreatedAt(LocalDateTime.now());
            CompletableFuture<UUID> idFuture = entityService.addItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, adoptionRequest);
            UUID technicalId = idFuture.get();

            processAdoptionRequest(adoptionRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(adoptionRequest);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid Pet id format");
        } catch (Exception e) {
            log.error("Failed to create AdoptionRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create AdoptionRequest");
        }
    }

    // GET /controller/adoptionRequest/{id}
    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ADOPTION_REQUEST_MODEL, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid AdoptionRequest id format");
        } catch (Exception e) {
            log.error("Failed to get AdoptionRequest {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get AdoptionRequest");
        }
    }

    // process methods with real business logic

    private void processPetIngestionJob(PetIngestionJob job) {
        log.info("Processing PetIngestionJob");
        job.setStatus("PROCESSING");
        try {
            if (!job.getSource().startsWith("http")) {
                throw new IllegalArgumentException("Invalid source URL");
            }
            // Simulate fetching pet data from Petstore API - create dummy pets for demo
            Pet pet1 = new Pet();
            pet1.setName("Fluffy");
            pet1.setType("Cat");
            pet1.setStatus("AVAILABLE");
            pet1.setCreatedAt(LocalDateTime.now());
            entityService.addItem(PET_MODEL, ENTITY_VERSION, pet1).get();
            processPet(pet1);

            Pet pet2 = new Pet();
            pet2.setName("Buddy");
            pet2.setType("Dog");
            pet2.setStatus("AVAILABLE");
            pet2.setCreatedAt(LocalDateTime.now());
            entityService.addItem(PET_MODEL, ENTITY_VERSION, pet2).get();
            processPet(pet2);

            job.setStatus("COMPLETED");
            log.info("PetIngestionJob completed successfully");
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("PetIngestionJob failed: {}", e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet");
        if (!pet.isValid()) {
            log.error("Pet is invalid");
            return;
        }
        log.info("Pet is ready and available with status {}", pet.getStatus());
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        log.info("Processing AdoptionRequest");
        try {
            UUID petUuid = UUID.fromString(request.getPetId());
            CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, petUuid);
            ObjectNode petNode = petNodeFuture.get();
            if (petNode == null || petNode.isEmpty()) {
                log.error("Referenced pet does not exist for adoption request");
                request.setStatus("REJECTED");
                return;
            }
            Pet pet = entityService.getObjectMapper().treeToValue(petNode, Pet.class);
            if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
                log.info("Pet is not available for adoption, rejecting request");
                request.setStatus("REJECTED");
                return;
            }
            request.setStatus("APPROVED");
            log.info("AdoptionRequest approved for pet");

            Pet adoptedPet = new Pet();
            adoptedPet.setName(pet.getName());
            adoptedPet.setType(pet.getType());
            adoptedPet.setStatus("ADOPTED");
            adoptedPet.setCreatedAt(LocalDateTime.now());
            entityService.addItem(PET_MODEL, ENTITY_VERSION, adoptedPet).get();

            log.info("Pet status updated to ADOPTED by creating new entity");
        } catch (Exception e) {
            log.error("Failed processing adoption request: {}", e.getMessage());
            request.setStatus("REJECTED");
        }
    }
}