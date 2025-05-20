package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_NAME = "pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("Starting CyodaEntityControllerPrototype");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private UUID technicalId;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        @Size(max = 50)
        private String type;
        @Size(max = 50)
        private String status;
        @Size(max = 50)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 50)
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        private List<@NotBlank @Size(max = 200) String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePetRequest {
        @Size(max = 100)
        private String name;
        @Size(max = 50)
        private String type;
        @Pattern(regexp = "available|pending|sold")
        private String status;
        private List<@NotBlank @Size(max = 200) String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private UUID id;
        private String message;
    }

    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid SearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received search request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());

        ArrayNode allItems = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();

        List<Pet> results = new ArrayList<>();
        for (JsonNode node : allItems) {
            Pet pet = mapObjectNodeToPet((ObjectNode) node);
            if (filterPet(pet, request)) {
                results.add(pet);
            }
        }
        return ResponseEntity.ok(results);
    }

    @PostMapping
    public ResponseEntity<MessageResponse> addPet(@RequestBody @Valid AddPetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Adding new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());
        Pet pet = new Pet(null, request.getName(), request.getType(), request.getStatus(), request.getPhotoUrls());

        // Removed workflow function for async pre-persistence processing
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet);

        UUID technicalId = idFuture.get();
        return ResponseEntity.status(201).body(new MessageResponse(technicalId, "Pet added successfully"));
    }

    @PostMapping("/{id}/update")
    public ResponseEntity<MessageResponse> updatePet(@PathVariable UUID id, @RequestBody @Valid UpdatePetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Updating pet id={}", id);
        ObjectNode existingNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id).get();
        if (existingNode == null || existingNode.isEmpty(null)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found: " + id);
        }

        // Apply updates directly to ObjectNode
        if (request.getName() != null) existingNode.put("name", request.getName());
        if (request.getType() != null) existingNode.put("type", request.getType());
        if (request.getStatus() != null) existingNode.put("status", request.getStatus());
        if (request.getPhotoUrls() != null) {
            ArrayNode photoArray = existingNode.putArray("photoUrls");
            for (String url : request.getPhotoUrls()) {
                photoArray.add(url);
            }
        }

        // Removed workflow function on update for consistency
        CompletableFuture<UUID> updatedFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, existingNode);
        UUID updatedId = updatedFuture.get();

        return ResponseEntity.ok(new MessageResponse(updatedId, "Pet updated successfully"));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<MessageResponse> deletePet(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        logger.info("Deleting pet id={}", id);
        ObjectNode existing = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id).get();
        if (existing == null || existing.isEmpty(null)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found: " + id);
        }
        CompletableFuture<UUID> deletedFuture = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
        UUID deletedId = deletedFuture.get();
        return ResponseEntity.ok(new MessageResponse(deletedId, "Pet deleted successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        logger.info("Retrieving pet id={}", id);
        ObjectNode node = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id).get();
        if (node == null || node.isEmpty(null)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found: " + id);
        }
        Pet pet = mapObjectNodeToPet(node);
        return ResponseEntity.ok(pet);
    }

    @GetMapping
    public ResponseEntity<List<Pet>> listAllPets() throws ExecutionException, InterruptedException {
        logger.info("Listing all pets");
        ArrayNode allItems = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : allItems) {
            pets.add(mapObjectNodeToPet((ObjectNode) node));
        }
        return ResponseEntity.ok(pets);
    }

    // Utility: map ObjectNode to Pet POJO
    private Pet mapObjectNodeToPet(ObjectNode node) {
        try {
            UUID technicalId = node.hasNonNull("technicalId") ? UUID.fromString(node.get("technicalId").asText()) : null;
            String name = node.hasNonNull("name") ? node.get("name").asText() : "";
            String status = node.hasNonNull("status") ? node.get("status").asText() : "";
            String type = node.hasNonNull("type") ? node.get("type").asText() : "";
            List<String> photoUrls = new ArrayList<>();
            if (node.hasNonNull("photoUrls") && node.get("photoUrls").isArray()) {
                for (JsonNode urlNode : node.get("photoUrls")) {
                    photoUrls.add(urlNode.asText());
                }
            }
            return new Pet(technicalId, name, type, status, photoUrls);
        } catch (Exception e) {
            logger.error("Failed to map ObjectNode to Pet", e);
            return null;
        }
    }

    // Utility: filters pet by search criteria
    private boolean filterPet(Pet pet, SearchRequest req) {
        if (pet == null) return false;
        if (req.getType() != null && !req.getType().isBlank()) {
            if (pet.getType() == null || !pet.getType().equalsIgnoreCase(req.getType())) {
                return false;
            }
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if (pet.getStatus() == null || !pet.getStatus().equalsIgnoreCase(req.getStatus())) {
                return false;
            }
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            if (pet.getName() == null || !pet.getName().toLowerCase().contains(req.getName().toLowerCase())) {
                return false;
            }
        }
        return true;
    }
}