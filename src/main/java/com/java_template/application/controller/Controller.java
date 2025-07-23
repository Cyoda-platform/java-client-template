package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // --- PetJob Endpoints ---

    @PostMapping("/petjobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException {
        if (petJob.getPetType() == null || petJob.getPetType().isBlank()) {
            logger.error("PetJob creation failed: petType is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'petType' is required");
        }
        petJob.setStatus("PENDING");
        petJob.setRequestedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetJob",
                ENTITY_VERSION,
                petJob
        );
        UUID technicalId = idFuture.get();

        petJobProcessing(technicalId, petJob.getPetType());

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", technicalId.toString());
        response.put("status", "PENDING");
        logger.info("Created PetJob with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/petjobs/{jobId}")
    public ResponseEntity<?> getPetJob(@PathVariable String jobId) throws ExecutionException, InterruptedException {
        UUID uuid = UUID.fromString(jobId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetJob", ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null) {
            logger.error("PetJob not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(item);
    }

    private void petJobProcessing(UUID technicalId, String petType) {
        // This method simulates processing asynchronously, but here we do synchronously for prototype.

        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetJob", ENTITY_VERSION, technicalId);
            ObjectNode petJobNode = itemFuture.get();
            if (petJobNode == null) {
                logger.error("PetJob not found during processing: {}", technicalId);
                return;
            }

            // Validate petType
            if (petType == null || petType.isBlank()) {
                logger.error("Invalid petType in PetJob: {}", petType);
                petJobNode.put("status", "FAILED");
                entityService.updateItem("PetJob", ENTITY_VERSION, technicalId, petJobNode).get();
                return;
            }

            petJobNode.put("status", "PROCESSING");
            entityService.updateItem("PetJob", ENTITY_VERSION, technicalId, petJobNode).get();

            // Simulate fetching pets from Petstore API filtered by petType (create dummy pet)
            Pet dummyPet = new Pet();
            dummyPet.setPetId(null); // id will be assigned by entityService
            dummyPet.setName("DummyPet_" + UUID.randomUUID());
            dummyPet.setType(petType);
            dummyPet.setStatus("ACTIVE");

            CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", ENTITY_VERSION, dummyPet);
            UUID petTechnicalId = petIdFuture.get();
            logger.info("PetJob processed: Created dummy Pet with technicalId {}", petTechnicalId);

            petJobNode.put("status", "COMPLETED");
            entityService.updateItem("PetJob", ENTITY_VERSION, technicalId, petJobNode).get();

        } catch (Exception e) {
            logger.error("Error processing PetJob with technicalId {}: {}", technicalId, e.getMessage());
            // no try-catch propagation per instructions, but this is internal method
        }
    }

    // --- Pet Endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            logger.error("Pet creation failed: petId is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'petId' is required");
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet creation failed: name is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'name' is required");
        }
        if (pet.getType() == null || pet.getType().isBlank()) {
            logger.error("Pet creation failed: type is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'type' is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            logger.error("Pet creation failed: status is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'status' is required");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Pet",
                ENTITY_VERSION,
                pet
        );
        UUID technicalId = idFuture.get();

        petProcessing(technicalId);

        CompletableFuture<ObjectNode> createdPetFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = createdPetFuture.get();

        logger.info("Created Pet with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(petNode);
    }

    @GetMapping("/pets/{petId}")
    public ResponseEntity<?> getPet(@PathVariable String petId) throws ExecutionException, InterruptedException {
        UUID uuid = UUID.fromString(petId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null) {
            logger.error("Pet not found: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(item);
    }

    private void petProcessing(UUID technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
            ObjectNode petNode = itemFuture.get();
            if (petNode == null) {
                logger.error("Pet not found during processing: {}", technicalId);
                return;
            }

            // Validate pet data completeness
            String petId = petNode.path("petId").asText(null);
            String name = petNode.path("name").asText(null);
            String type = petNode.path("type").asText(null);
            String status = petNode.path("status").asText(null);

            if (petId == null || petId.isBlank() ||
                    name == null || name.isBlank() ||
                    type == null || type.isBlank() ||
                    status == null || status.isBlank()) {
                logger.error("Pet entity validation failed for technicalId: {}", technicalId);
                return;
            }

            // Enrich with fun facts (prototype static example)
            logger.info("Enriched Pet {} with fun facts", technicalId);

        } catch (Exception e) {
            logger.error("Error processing Pet with technicalId {}: {}", technicalId, e.getMessage());
        }
    }

    // --- AdoptionRequest Endpoints ---

    @PostMapping("/adoptionrequests")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) throws ExecutionException, InterruptedException {
        if (adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isBlank()) {
            logger.error("AdoptionRequest creation failed: petId is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'petId' is required");
        }
        if (adoptionRequest.getRequesterName() == null || adoptionRequest.getRequesterName().isBlank()) {
            logger.error("AdoptionRequest creation failed: requesterName is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Field 'requesterName' is required");
        }

        adoptionRequest.setStatus("PENDING");
        adoptionRequest.setRequestedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "AdoptionRequest",
                ENTITY_VERSION,
                adoptionRequest
        );
        UUID technicalId = idFuture.get();

        adoptionRequestProcessing(technicalId, adoptionRequest.getPetId());

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", technicalId.toString());

        CompletableFuture<ObjectNode> createdRequestFuture = entityService.getItem("AdoptionRequest", ENTITY_VERSION, technicalId);
        ObjectNode requestNode = createdRequestFuture.get();
        if (requestNode != null && requestNode.has("status")) {
            response.put("status", requestNode.get("status").asText());
        } else {
            response.put("status", "PENDING");
        }

        logger.info("Created AdoptionRequest with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/adoptionrequests/{requestId}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String requestId) throws ExecutionException, InterruptedException {
        UUID uuid = UUID.fromString(requestId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("AdoptionRequest", ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null) {
            logger.error("AdoptionRequest not found: {}", requestId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(item);
    }

    private void adoptionRequestProcessing(UUID technicalId, String petId) {
        try {
            CompletableFuture<ObjectNode> adoptionRequestFuture = entityService.getItem("AdoptionRequest", ENTITY_VERSION, technicalId);
            ObjectNode adoptionRequestNode = adoptionRequestFuture.get();
            if (adoptionRequestNode == null) {
                logger.error("AdoptionRequest not found during processing: {}", technicalId);
                return;
            }

            // Check pet availability
            // build condition for pet with petId and status ACTIVE (case insensitive)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", petId),
                    Condition.of("$.status", "IEQUALS", "ACTIVE"));

            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
            ArrayNode pets = petsFuture.get();

            if (pets == null || pets.isEmpty()) {
                adoptionRequestNode.put("status", "REJECTED");
                logger.error("Pet not available for AdoptionRequest technicalId: {}", technicalId);
            } else {
                adoptionRequestNode.put("status", "APPROVED");
            }

            entityService.updateItem("AdoptionRequest", ENTITY_VERSION, technicalId, adoptionRequestNode).get();

            logger.info("AdoptionRequest {} status set to {}", technicalId, adoptionRequestNode.get("status").asText());

        } catch (Exception e) {
            logger.error("Error processing AdoptionRequest with technicalId {}: {}", technicalId, e.getMessage());
        }
    }
}