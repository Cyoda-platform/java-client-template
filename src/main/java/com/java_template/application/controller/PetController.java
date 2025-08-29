package com.java_template.application.controller;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.java_template.common.config.Config;

@RestController
@RequestMapping("/pets")
public class PetController {

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Create a new pet.
     * Request:
     * {
     *     "name": "Fluffy",
     *     "type": "Cat",
     *     "age": 2,
     *     "status": "Available"
     * }
     * Response:
     * {
     *     "technicalId": "pet1234"
     * }
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<ObjectNode>> createPet(@RequestBody Pet pet) {
        try {
            // Generate ID for the pet
            pet.setId(UUID.randomUUID().toString());
            
            // Set default status if not provided
            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                pet.setStatus("Available");
            }

            // Validate the pet
            if (!pet.isValid()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Invalid pet data. All fields (name, type, age, status) are required.");
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(errorResponse)
                );
            }

            return entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet)
                .thenApply(technicalId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", technicalId.toString());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to create pet: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (Exception e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid request: " + e.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Retrieve pet details by technicalId.
     */
    @GetMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> getPet(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            return entityService.getItem(uuid)
                .thenApply(petData -> {
                    if (petData == null || petData.getData() == null) {
                        ObjectNode errorResponse = objectMapper.createObjectNode();
                        errorResponse.put("error", "Pet not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body((JsonNode) errorResponse);
                    }
                    return ResponseEntity.ok(petData.getData());
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to retrieve pet: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((JsonNode) errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Get all pets.
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<JsonNode>> getAllPets() {
        return entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null)
            .thenApply(pets -> {
                ObjectNode response = objectMapper.createObjectNode();
                response.set("pets", objectMapper.valueToTree(pets));
                return ResponseEntity.ok((JsonNode) response);
            })
            .exceptionally(throwable -> {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Failed to retrieve pets: " + throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((JsonNode) errorResponse);
            });
    }

    /**
     * Update a pet by technicalId.
     */
    @PutMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> updatePet(
            @PathVariable String technicalId, 
            @RequestBody Pet pet) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            // Set the ID to match the path parameter
            pet.setId(technicalId);

            // Validate the pet
            if (!pet.isValid()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Invalid pet data. All fields (name, type, age, status) are required.");
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(errorResponse)
                );
            }

            return entityService.updateItem(uuid, pet)
                .thenApply(updatedId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", updatedId.toString());
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to update pet: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Delete a pet by technicalId.
     */
    @DeleteMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> deletePet(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            return entityService.deleteItem(uuid)
                .thenApply(deletedId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", deletedId.toString());
                    response.put("message", "Pet deleted successfully");
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to delete pet: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }
}
