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
import java.util.*;
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

    // ------------------- PETJOB ENDPOINTS -------------------

    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (petJob == null || petJob.getRequestType() == null || petJob.getRequestType().isBlank()) {
            logger.error("Invalid PetJob creation request: Missing requestType");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid requestType");
        }
        petJob.setStatus(PetJob.StatusEnum.PENDING);
        petJob.setCreatedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("PetJob", ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);

        logger.info("Created PetJob with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PetJob technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetJob", ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            logger.error("PetJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        PetJob petJob = objectMapper.treeToValue(item, PetJob.class);
        return ResponseEntity.ok(petJob);
    }

    // ------------------- PET ENDPOINTS -------------------

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Pet technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            logger.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = objectMapper.treeToValue(item, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // ------------------- ADOPTIONREQUEST ENDPOINTS -------------------

    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (adoptionRequest == null
                || adoptionRequest.getPetId() == null
                || adoptionRequest.getRequesterName() == null || adoptionRequest.getRequesterName().isBlank()
                || adoptionRequest.getContactInfo() == null || adoptionRequest.getContactInfo().isBlank()) {
            logger.error("Invalid AdoptionRequest creation request: Missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields");
        }

        // Validate Pet existence and availability via entityService
        UUID petTechnicalId;
        try {
            petTechnicalId = UUID.fromString(adoptionRequest.getPetId().toString());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Pet technicalId format in AdoptionRequest: {}", adoptionRequest.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet ID format");
        }

        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, petTechnicalId);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            logger.error("Pet not found for AdoptionRequest with petId: {}", adoptionRequest.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced Pet does not exist");
        }
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        if (pet.getStatus() != Pet.StatusEnum.AVAILABLE) {
            logger.error("Pet with technicalId {} is not available for adoption", adoptionRequest.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet is not available for adoption");
        }

        adoptionRequest.setStatus(AdoptionRequest.StatusEnum.REQUESTED);
        adoptionRequest.setRequestedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("AdoptionRequest", ENTITY_VERSION, adoptionRequest);
        UUID technicalId = idFuture.get();
        adoptionRequest.setTechnicalId(technicalId);

        logger.info("Created AdoptionRequest with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(adoptionRequest);
    }

    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid AdoptionRequest technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid AdoptionRequest ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("AdoptionRequest", ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            logger.error("AdoptionRequest not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        AdoptionRequest adoptionRequest = objectMapper.treeToValue(item, AdoptionRequest.class);
        return ResponseEntity.ok(adoptionRequest);
    }
}