package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-pets")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "pet";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public CompletableFuture<AddOrUpdateResponse> addOrUpdatePet(@RequestBody @Valid Pet pet) {
        ObjectNode petNode = objectMapper.valueToTree(pet);

        if (pet.getId() != null) {
            // Update existing pet directly, no workflow function for update
            UUID technicalId = pet.getId();
            return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petNode)
                    .thenApply(updatedId -> {
                        logger.info("Updated pet with technicalId {}", updatedId);
                        return new AddOrUpdateResponse(true, updatedId, "Pet updated successfully");
                    });
        } else {
            // Add new pet without workflow function
            return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode)
                    .thenApply(createdId -> {
                        logger.info("Added pet with technicalId {}", createdId);
                        return new AddOrUpdateResponse(true, createdId, "Pet added successfully");
                    });
        }
    }

    @PostMapping("/search")
    public CompletableFuture<List<Pet>> searchPets(@RequestBody @Valid PetSearchRequest request) {
        List<String> conditions = new ArrayList<>();
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            conditions.add(String.format("category=='%s'", escapeQuotes(request.getCategory())));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditions.add(String.format("status=='%s'", escapeQuotes(request.getStatus())));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            conditions.add(String.format("name like '*%s*'", escapeQuotes(request.getName())));
        }
        String condition = String.join(" and ", conditions);

        CompletableFuture<ArrayNode> filteredItemsFuture;
        if (condition.isEmpty()) {
            filteredItemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        } else {
            filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        }

        return filteredItemsFuture.thenApply(arrayNode -> {
            List<Pet> result = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                Pet pet = convertNodeToPet(node);
                result.add(pet);
            }
            return result;
        });
    }

    @GetMapping("/{id}")
    public CompletableFuture<Pet> getPetById(@PathVariable @NotNull UUID id) {
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(node -> {
                    if (node == null || node.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                    }
                    return convertNodeToPet(node);
                });
    }

    @PostMapping("/{id}/delete")
    public CompletableFuture<SimpleResponse> deletePet(@PathVariable @NotNull UUID id) {
        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(deletedId -> {
                    logger.info("Deleted pet with technicalId {}", deletedId);
                    return new SimpleResponse(true, "Pet deleted successfully");
                });
    }

    private Pet convertNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            try {
                pet.setId(UUID.fromString(node.get("technicalId").asText()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid UUID format for technicalId: {}", node.get("technicalId").asText());
                pet.setId(null);
            }
        }
        if (node.has("name")) {
            pet.setName(node.get("name").asText());
        }
        if (node.has("category")) {
            pet.setCategory(node.get("category").asText());
        }
        if (node.has("status")) {
            pet.setStatus(node.get("status").asText());
        }
        if (node.has("tags") && node.get("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : node.get("tags")) {
                tags.add(tagNode.asText());
            }
            pet.setTags(tags);
        } else {
            pet.setTags(Collections.emptyList());
        }
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            List<String> photoUrls = new ArrayList<>();
            for (JsonNode urlNode : node.get("photoUrls")) {
                photoUrls.add(urlNode.asText());
            }
            pet.setPhotoUrls(photoUrls);
        } else {
            pet.setPhotoUrls(Collections.emptyList());
        }
        return pet;
    }

    private String escapeQuotes(String input) {
        if (input == null) return "";
        return input.replace("'", "\\'");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private UUID id;
        @NotBlank
        private String name;
        @NotBlank
        private String category;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> tags;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOrUpdateResponse {
        private boolean success;
        private UUID petId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetSearchRequest {
        @Size(min = 1)
        private String category;
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(min = 1)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleResponse {
        private boolean success;
        private String message;
    }
}