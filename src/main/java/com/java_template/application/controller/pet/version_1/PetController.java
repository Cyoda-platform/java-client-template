package com.java_template.application.controller.pet.version_1;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pets")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<JsonNode>> addPet(@Valid @RequestBody Pet pet) {
        logger.info("Adding new pet: {}", pet.getName());
        
        return entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet)
            .thenApply(technicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", technicalId.toString())
                        .put("message", "Pet added successfully");
                    logger.info("Pet added successfully with technicalId: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } catch (Exception e) {
                    logger.error("Error creating response for pet: {}", pet.getName(), e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error adding pet: {}", pet.getName(), throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to add pet")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @GetMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> getPet(@PathVariable UUID technicalId) {
        logger.info("Retrieving pet with technicalId: {}", technicalId);
        
        return entityService.getItem(technicalId)
            .thenApply(dataPayload -> {
                try {
                    if (dataPayload != null && dataPayload.getData() != null) {
                        JsonNode petData = dataPayload.getData();
                        // Add technicalId to the response
                        if (petData.isObject()) {
                            ((com.fasterxml.jackson.databind.node.ObjectNode) petData)
                                .put("technicalId", technicalId.toString());
                        }
                        logger.info("Pet retrieved successfully: {}", technicalId);
                        return ResponseEntity.ok(petData);
                    } else {
                        logger.warn("Pet not found with technicalId: {}", technicalId);
                        JsonNode errorResponse = objectMapper.createObjectNode()
                            .put("error", "Pet not found")
                            .put("technicalId", technicalId.toString());
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                    }
                } catch (Exception e) {
                    logger.error("Error processing pet data for technicalId: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to process pet data")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error retrieving pet with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to retrieve pet")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<JsonNode>> getAllPets(
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(defaultValue = "1") Integer pageNumber) {
        logger.info("Retrieving all pets with pageSize: {} and pageNumber: {}", pageSize, pageNumber);
        
        return entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pageSize, pageNumber, null)
            .thenApply(dataPayloads -> {
                try {
                    List<JsonNode> pets = dataPayloads.stream()
                        .map(DataPayload::getData)
                        .collect(Collectors.toList());
                    
                    JsonNode response = objectMapper.createObjectNode()
                        .put("count", pets.size())
                        .set("pets", objectMapper.valueToTree(pets));
                    
                    logger.info("Retrieved {} pets successfully", pets.size());
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error processing pets data", e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to process pets data")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error retrieving pets", throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to retrieve pets")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @PutMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> updatePet(
            @PathVariable UUID technicalId, 
            @Valid @RequestBody Pet pet) {
        logger.info("Updating pet with technicalId: {}", technicalId);
        
        return entityService.updateItem(technicalId, pet)
            .thenApply(updatedTechnicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", updatedTechnicalId.toString())
                        .put("message", "Pet updated successfully");
                    logger.info("Pet updated successfully with technicalId: {}", updatedTechnicalId);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error creating response for pet update: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error updating pet with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to update pet")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @DeleteMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> deletePet(@PathVariable UUID technicalId) {
        logger.info("Deleting pet with technicalId: {}", technicalId);
        
        return entityService.deleteItem(technicalId)
            .thenApply(deletedTechnicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", deletedTechnicalId.toString())
                        .put("message", "Pet deleted successfully");
                    logger.info("Pet deleted successfully with technicalId: {}", deletedTechnicalId);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error creating response for pet deletion: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error deleting pet with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to delete pet")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }
}
