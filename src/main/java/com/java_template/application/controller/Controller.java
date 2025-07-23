package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // --- PetJob Endpoints ---

    @PostMapping("/petjobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException, JsonProcessingException {
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

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", technicalId.toString());
        response.put("status", "PENDING");
        logger.info("Created PetJob with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/petjobs/{jobId}")
    public ResponseEntity<?> getPetJob(@PathVariable String jobId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(jobId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetJob", ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null) {
            logger.error("PetJob not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = objectMapper.treeToValue(item, PetJob.class);
        return ResponseEntity.ok(petJob);
    }

    // --- Pet Endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
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

        CompletableFuture<ObjectNode> createdPetFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = createdPetFuture.get();

        Pet createdPet = objectMapper.treeToValue(petNode, Pet.class);

        logger.info("Created Pet with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPet);
    }

    @GetMapping("/pets/{petId}")
    public ResponseEntity<?> getPet(@PathVariable String petId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(petId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null) {
            logger.error("Pet not found: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(item, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // --- AdoptionRequest Endpoints ---

    @PostMapping("/adoptionrequests")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) throws ExecutionException, InterruptedException, JsonProcessingException {
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
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String requestId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(requestId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("AdoptionRequest", ENTITY_VERSION, uuid);
        ObjectNode item = itemFuture.get();
        if (item == null) {
            logger.error("AdoptionRequest not found: {}", requestId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        AdoptionRequest adoptionRequest = objectMapper.treeToValue(item, AdoptionRequest.class);
        return ResponseEntity.ok(adoptionRequest);
    }
}