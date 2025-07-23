package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;
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
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // ------------------ PetIngestionJob Endpoints ------------------

    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob jobRequest) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (jobRequest == null || jobRequest.getSource() == null || jobRequest.getSource().isBlank()) {
            logger.error("Invalid PetIngestionJob creation request: missing source");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: source");
        }

        PetIngestionJob newJob = new PetIngestionJob();
        // id and jobId will be assigned after saving - technicalId is UUID in external service
        newJob.setSource(jobRequest.getSource());
        newJob.setStatus("PENDING");
        newJob.setCreatedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "petIngestionJob",
                ENTITY_VERSION,
                newJob
        );

        UUID technicalId = idFuture.get();

        // Set technicalId as String id for response consistency
        String idString = technicalId.toString();
        newJob.setId(idString);
        newJob.setJobId(idString);
        newJob.setTechnicalId(technicalId);

        logger.info("Created PetIngestionJob with technicalId: {}", idString);

        return ResponseEntity.status(HttpStatus.CREATED).body(newJob);
    }

    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PetIngestionJob ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "petIngestionJob",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode node = itemFuture.get();

        if (node == null || node.isNull() || node.isEmpty()) {
            logger.error("PetIngestionJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }

        PetIngestionJob job = objectMapper.treeToValue(node, PetIngestionJob.class);
        job.setId(id);
        job.setJobId(id);
        job.setTechnicalId(technicalId);

        return ResponseEntity.ok(job);
    }

    // ------------------ Pet Endpoints ------------------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet petRequest) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (petRequest == null || !validatePetRequest(petRequest)) {
            logger.error("Invalid Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required pet fields");
        }

        Pet newPet = new Pet();
        // id and petId assigned after saving
        newPet.setName(petRequest.getName());
        newPet.setCategory(petRequest.getCategory());
        newPet.setPhotoUrls(petRequest.getPhotoUrls() != null ? petRequest.getPhotoUrls() : new ArrayList<>());
        newPet.setTags(petRequest.getTags() != null ? petRequest.getTags() : new ArrayList<>());
        newPet.setStatus("NEW");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "pet",
                ENTITY_VERSION,
                newPet
        );

        UUID technicalId = idFuture.get();
        String idString = technicalId.toString();
        newPet.setId(idString);
        newPet.setPetId(idString);
        newPet.setTechnicalId(technicalId);

        logger.info("Created Pet with technicalId: {}", idString);

        return ResponseEntity.status(HttpStatus.CREATED).body(newPet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Pet ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "pet",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode node = itemFuture.get();

        if (node == null || node.isNull() || node.isEmpty()) {
            logger.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        Pet pet = objectMapper.treeToValue(node, Pet.class);
        pet.setId(id);
        pet.setPetId(id);
        pet.setTechnicalId(technicalId);

        return ResponseEntity.ok(pet);
    }

    // ------------------ Helper Methods ------------------

    private boolean validatePetRequest(Pet pet) {
        return pet != null
                && pet.getName() != null && !pet.getName().isBlank()
                && pet.getCategory() != null && !pet.getCategory().isBlank();
    }
}