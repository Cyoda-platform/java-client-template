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

    // ------------------- PETJOB ENDPOINTS -------------------

    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException {
        if (petJob == null || petJob.getRequestType() == null || petJob.getRequestType().isBlank()) {
            logger.error("Invalid PetJob creation request: Missing requestType");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid requestType");
        }
        petJob.setStatus(PetJob.StatusEnum.PENDING);
        petJob.setCreatedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("PetJob", ENTITY_VERSION, petJob);
        UUID technicalId = idFuture.get();
        petJob.setTechnicalId(technicalId);

        // processPetJob method removed as per extraction

        logger.info("Created PetJob with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        // id is expected to be the technicalId string (UUID)
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
        PetJob petJob = convertObjectNodeToPetJob(item);
        return ResponseEntity.ok(petJob);
    }

    // ------------------- PET ENDPOINTS -------------------

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
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
        Pet pet = convertObjectNodeToPet(item);
        return ResponseEntity.ok(pet);
    }

    // ------------------- ADOPTIONREQUEST ENDPOINTS -------------------

    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) throws ExecutionException, InterruptedException {
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
            petTechnicalId = UUID.fromString(adoptionRequest.getPetId());
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
        Pet pet = convertObjectNodeToPet(petNode);
        if (pet.getStatus() != Pet.StatusEnum.AVAILABLE) {
            logger.error("Pet with technicalId {} is not available for adoption", adoptionRequest.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet is not available for adoption");
        }

        adoptionRequest.setStatus(AdoptionRequest.StatusEnum.REQUESTED);
        adoptionRequest.setRequestedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("AdoptionRequest", ENTITY_VERSION, adoptionRequest);
        UUID technicalId = idFuture.get();
        adoptionRequest.setTechnicalId(technicalId);

        // processAdoptionRequest method removed as per extraction

        logger.info("Created AdoptionRequest with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(adoptionRequest);
    }

    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) throws ExecutionException, InterruptedException {
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
        AdoptionRequest adoptionRequest = convertObjectNodeToAdoptionRequest(item);
        return ResponseEntity.ok(adoptionRequest);
    }

    // ------------- Conversion helpers -------------

    private PetJob convertObjectNodeToPetJob(ObjectNode node) {
        PetJob petJob = new PetJob();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            petJob.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("id") && !node.get("id").isNull()) {
            petJob.setId(node.get("id").asText());
        }
        if (node.has("requestType") && !node.get("requestType").isNull()) {
            petJob.setRequestType(node.get("requestType").asText());
        }
        if (node.has("petType") && !node.get("petType").isNull()) {
            petJob.setPetType(node.get("petType").asText());
        }
        if (node.has("status") && !node.get("status").isNull()) {
            try {
                petJob.setStatus(PetJob.StatusEnum.valueOf(node.get("status").asText()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (node.has("createdAt") && !node.get("createdAt").isNull()) {
            petJob.setCreatedAt(LocalDateTime.parse(node.get("createdAt").asText()));
        }
        if (node.has("resultCount") && !node.get("resultCount").isNull()) {
            petJob.setResultCount(node.get("resultCount").asInt());
        }
        return petJob;
    }

    private Pet convertObjectNodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("id") && !node.get("id").isNull()) {
            try {
                pet.setId(node.get("id").asLong());
            } catch (Exception ignored) {
            }
        }
        if (node.has("name") && !node.get("name").isNull()) {
            pet.setName(node.get("name").asText());
        }
        if (node.has("category") && !node.get("category").isNull()) {
            pet.setCategory(node.get("category").asText());
        }
        if (node.has("photoUrls") && !node.get("photoUrls").isNull() && node.get("photoUrls").isArray()) {
            List<String> photoUrls = new ArrayList<>();
            node.get("photoUrls").forEach(pu -> photoUrls.add(pu.asText()));
            pet.setPhotoUrls(photoUrls);
        }
        if (node.has("tags") && !node.get("tags").isNull() && node.get("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            node.get("tags").forEach(t -> tags.add(t.asText()));
            pet.setTags(tags);
        }
        if (node.has("status") && !node.get("status").isNull()) {
            try {
                pet.setStatus(Pet.StatusEnum.valueOf(node.get("status").asText()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return pet;
    }

    private AdoptionRequest convertObjectNodeToAdoptionRequest(ObjectNode node) {
        AdoptionRequest ar = new AdoptionRequest();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            ar.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("id") && !node.get("id").isNull()) {
            ar.setId(node.get("id").asText());
        }
        if (node.has("petId") && !node.get("petId").isNull()) {
            ar.setPetId(node.get("petId").asText());
        }
        if (node.has("requesterName") && !node.get("requesterName").isNull()) {
            ar.setRequesterName(node.get("requesterName").asText());
        }
        if (node.has("contactInfo") && !node.get("contactInfo").isNull()) {
            ar.setContactInfo(node.get("contactInfo").asText());
        }
        if (node.has("status") && !node.get("status").isNull()) {
            try {
                ar.setStatus(AdoptionRequest.StatusEnum.valueOf(node.get("status").asText()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (node.has("requestedAt") && !node.get("requestedAt").isNull()) {
            ar.setRequestedAt(LocalDateTime.parse(node.get("requestedAt").asText()));
        }
        return ar;
    }

}