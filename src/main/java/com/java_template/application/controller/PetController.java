package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST controller for Pet operations.
 * Provides endpoints for managing pets in the store.
 */
@RestController
@RequestMapping("/pet")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Add a new pet to the store
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<Pet>> addPet(@Valid @RequestBody Pet pet) {
        logger.info("Adding new pet: {}", pet.getName());

        try {
            return entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet)
                .thenCompose(entityId -> entityService.getItem(entityId))
                .thenApply(dataPayload -> {
                    try {
                        Pet savedPet = objectMapper.treeToValue(dataPayload.getData(), Pet.class);
                        return ResponseEntity.status(HttpStatus.CREATED).body(savedPet);
                    } catch (Exception e) {
                        logger.error("Error converting saved pet data", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                });
        } catch (Exception e) {
            logger.error("Error adding pet", e);
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            );
        }
    }

    /**
     * Update an existing pet
     */
    @PutMapping
    public CompletableFuture<ResponseEntity<Pet>> updatePet(@Valid @RequestBody Pet pet) {
        logger.info("Updating pet with ID: {}", pet.getId());
        
        if (pet.getId() == null) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().build()
            );
        }

        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(pet.getId().toString().getBytes());
        
        return entityService.updateItem(entityId, pet)
            .thenCompose(updatedId -> entityService.getItem(updatedId))
            .thenApply(dataPayload -> {
                try {
                    Pet updatedPet = objectMapper.treeToValue(dataPayload.getData(), Pet.class);
                    return ResponseEntity.ok(updatedPet);
                } catch (Exception e) {
                    logger.error("Error converting updated pet data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error updating pet", throwable);
                return (ResponseEntity<Pet>) ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }

    /**
     * Find pets by status
     */
    @GetMapping("/findByStatus")
    public CompletableFuture<ResponseEntity<List<Pet>>> findPetsByStatus(
            @RequestParam("status") List<String> statuses) {
        logger.info("Finding pets by status: {}", statuses);
        
        return entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null)
            .thenApply(dataPayloads -> {
                try {
                    List<Pet> pets = dataPayloads.stream()
                        .map(dataPayload -> {
                            try {
                                return objectMapper.treeToValue(dataPayload.getData(), Pet.class);
                            } catch (Exception e) {
                                logger.warn("Error converting pet data", e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(pet -> statuses.contains(getStatusFromMetadata(pet)))
                        .collect(Collectors.toList());
                    
                    return ResponseEntity.ok(pets);
                } catch (Exception e) {
                    logger.error("Error finding pets by status", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error finding pets by status", throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    /**
     * Find pets by tags
     */
    @GetMapping("/findByTags")
    public CompletableFuture<ResponseEntity<List<Pet>>> findPetsByTags(
            @RequestParam("tags") String tags) {
        logger.info("Finding pets by tags: {}", tags);
        
        List<String> tagNames = Arrays.asList(tags.split(","));
        
        return entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null)
            .thenApply(dataPayloads -> {
                try {
                    List<Pet> pets = dataPayloads.stream()
                        .map(dataPayload -> {
                            try {
                                return objectMapper.treeToValue(dataPayload.getData(), Pet.class);
                            } catch (Exception e) {
                                logger.warn("Error converting pet data", e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(pet -> pet.getTags() != null && 
                                pet.getTags().stream()
                                    .anyMatch(tag -> tagNames.contains(tag.getName())))
                        .collect(Collectors.toList());
                    
                    return ResponseEntity.ok(pets);
                } catch (Exception e) {
                    logger.error("Error finding pets by tags", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error finding pets by tags", throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    /**
     * Get pet by ID
     */
    @GetMapping("/{petId}")
    public CompletableFuture<ResponseEntity<Pet>> getPetById(@PathVariable Long petId) {
        logger.info("Getting pet by ID: {}", petId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(petId.toString().getBytes());
        
        return entityService.getItem(entityId)
            .thenApply(dataPayload -> {
                try {
                    Pet pet = objectMapper.treeToValue(dataPayload.getData(), Pet.class);
                    return ResponseEntity.ok(pet);
                } catch (Exception e) {
                    logger.error("Error converting pet data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error getting pet by ID", throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    /**
     * Delete pet by ID
     */
    @DeleteMapping("/{petId}")
    public CompletableFuture<ResponseEntity<Void>> deletePet(@PathVariable Long petId) {
        logger.info("Deleting pet by ID: {}", petId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(petId.toString().getBytes());
        
        return entityService.deleteItem(entityId)
            .thenApply(deletedId -> ResponseEntity.ok().<Void>build())
            .exceptionally(throwable -> {
                logger.error("Error deleting pet", throwable);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }

    /**
     * Helper method to extract status from entity metadata
     * In a real implementation, this would access the entity's workflow state
     */
    private String getStatusFromMetadata(Pet pet) {
        // Simplified status mapping - in reality this would come from entity metadata
        return "available"; // Default status
    }
}
