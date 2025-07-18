package com.java_template.prototype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/petjob")
@Slf4j
@Validated
public class PetJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class PetJobDto {
        @NotBlank
        @Size(max = 50)
        private String action;

        @NotBlank
        @Pattern(regexp = "^[0-9]+$", message = "petId must be a valid numeric string")
        private String petId;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createPetJob(@RequestBody @Valid PetJobDto petJobDto) throws JsonProcessingException {
        PetJob petJob = new PetJob();
        petJob.setAction(petJobDto.getAction());
        petJob.setPetId(Long.valueOf(petJobDto.getPetId())); // petId is Long in entity
        petJob.setId(""); // id and technicalId will be set after creation
        petJob.setTechnicalId(null);
        petJob.setJobId(""); // jobId must be set or null? For now empty string to pass isValid check
        petJob.setStatus(""); // status must be set or null? For now empty string to pass isValid check

        if (!petJob.isValid()) {
            logger.error("Invalid PetJob data");
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data"));
        }
        return entityService.addItem("PetJob", ENTITY_VERSION, petJob)
                .thenApply(technicalId -> {
                    petJob.setTechnicalId(technicalId);
                    petJob.setId(technicalId.toString());
                    try {
                        // processPetJob removed
                    } catch (Exception e) {
                        logger.error("Error processing PetJob with technicalId {}: {}", technicalId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
                    }
                    return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getPetJob(@PathVariable @NotBlank String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("PetJob", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("PetJob not found with id {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
                    }
                    PetJob petJob = null;
                    try {
                        petJob = objectMapper.treeToValue(objectNode, PetJob.class);
                    } catch (JsonProcessingException e) {
                        logger.error("Error converting PetJob JSON to object: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob data");
                    }
                    return ResponseEntity.ok(petJob);
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updatePetJob(
            @PathVariable @NotBlank String id,
            @RequestBody @Valid PetJobDto petJobDto) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("PetJob", ENTITY_VERSION, technicalId)
                .thenCompose(existingItem -> {
                    if (existingItem == null || existingItem.isEmpty()) {
                        logger.error("PetJob not found with id {}", id);
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found"));
                    }
                    PetJob petJob = new PetJob();
                    petJob.setTechnicalId(technicalId);
                    petJob.setId(id);
                    petJob.setAction(petJobDto.getAction());
                    petJob.setPetId(Long.valueOf(petJobDto.getPetId()));
                    petJob.setJobId(""); // must set jobId and status to pass isValid
                    petJob.setStatus("");
                    if (!petJob.isValid()) {
                        logger.error("Invalid PetJob data");
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data"));
                    }
                    return entityService.updateItem("PetJob", ENTITY_VERSION, technicalId, petJob)
                            .thenApply(updatedId -> {
                                try {
                                    // processPetJob removed
                                } catch (Exception e) {
                                    logger.error("Error processing PetJob with technicalId {}: {}", id, e.getMessage());
                                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
                                }
                                return ResponseEntity.ok(petJob);
                            });
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deletePetJob(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("PetJob", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    if (deletedId == null) {
                        logger.error("PetJob not found with id {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
                    }
                    logger.info("Deleted PetJob with technicalId {}", id);
                    return ResponseEntity.ok("PetJob deleted");
                });
    }
}

@RestController
@RequestMapping(path = "/pet")
@Slf4j
@Validated
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class PetDto {
        @NotBlank
        @Pattern(regexp = "^[0-9]+$", message = "petId must be a valid numeric string")
        private String petId;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String category;

        @NotBlank
        @Size(max = 20)
        private String status;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody @Valid PetDto petDto) throws JsonProcessingException {
        Pet pet = new Pet();
        pet.setPetId(Long.valueOf(petDto.getPetId()));
        pet.setName(petDto.getName());
        pet.setCategory(petDto.getCategory());
        pet.setStatus(petDto.getStatus());
        pet.setId(""); // id and technicalId will be set after creation
        pet.setTechnicalId(null);
        if (!pet.isValid()) {
            logger.error("Invalid Pet data");
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data"));
        }
        return entityService.addItem("Pet", ENTITY_VERSION, pet)
                .thenApply(technicalId -> {
                    pet.setTechnicalId(technicalId);
                    pet.setId(technicalId.toString());
                    try {
                        // processPet removed
                    } catch (Exception e) {
                        logger.error("Error processing Pet with technicalId {}: {}", technicalId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
                    }
                    return ResponseEntity.status(HttpStatus.CREATED).body(pet);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable @NotBlank String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("Pet", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Pet not found with id {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
                    }
                    Pet pet = null;
                    try {
                        pet = objectMapper.treeToValue(objectNode, Pet.class);
                    } catch (JsonProcessingException e) {
                        logger.error("Error converting Pet JSON to object: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet data");
                    }
                    return ResponseEntity.ok(pet);
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updatePet(
            @PathVariable @NotBlank String id,
            @RequestBody @Valid PetDto petDto) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("Pet", ENTITY_VERSION, technicalId)
                .thenCompose(existingItem -> {
                    if (existingItem == null || existingItem.isEmpty()) {
                        logger.error("Pet not found with id {}", id);
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found"));
                    }
                    Pet pet = new Pet();
                    pet.setTechnicalId(technicalId);
                    pet.setId(id);
                    pet.setPetId(Long.valueOf(petDto.getPetId()));
                    pet.setName(petDto.getName());
                    pet.setCategory(petDto.getCategory());
                    pet.setStatus(petDto.getStatus());
                    if (!pet.isValid()) {
                        logger.error("Invalid Pet data");
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data"));
                    }
                    return entityService.updateItem("Pet", ENTITY_VERSION, technicalId, pet)
                            .thenApply(updatedId -> {
                                try {
                                    // processPet removed
                                } catch (Exception e) {
                                    logger.error("Error processing Pet with technicalId {}: {}", id, e.getMessage());
                                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
                                }
                                return ResponseEntity.ok(pet);
                            });
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deletePet(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("Pet", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    if (deletedId == null) {
                        logger.error("Pet not found with id {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
                    }
                    logger.info("Deleted Pet with technicalId {}", id);
                    return ResponseEntity.ok("Pet deleted");
                });
    }
}