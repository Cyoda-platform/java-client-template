package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private UUID technicalId; // replaced id with technicalId for entityService
        private String name;
        private String type;
        private String status;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String type;
        private String status;
        private List<@NotBlank String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotBlank
        private String status;
        private List<@NotBlank String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePetRequest {
        private String name;
        private String type;
        private String status;
        private List<@NotBlank String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetResponse {
        private UUID id;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private List<Pet> pets;
    }

    // Workflow function that processes the pet entity asynchronously before persistence.
    // It modifies the entity directly and returns a CompletableFuture of the entity.
    private CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        logger.info("Workflow processPet started for entity: {}", entity);

        // Set default status if missing or empty
        if (!entity.hasNonNull("status") || entity.get("status").asText().trim().isEmpty()) {
            entity.put("status", "available");
            logger.info("Set default status=available");
        }

        // Defensive: Ensure tags field is an array if present, else create empty array for consistent processing
        if (!entity.has("tags") || !entity.get("tags").isArray()) {
            entity.putArray("tags");
        }

        // Example async enrichment: fetch external info from pet store API by pet name (if present)
        if (entity.hasNonNull("name")) {
            String petName = entity.get("name").asText().trim();

            if (!petName.isEmpty()) {
                // Run async enrichment in supplyAsync to avoid blocking
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=available";
                        String response = restTemplate.getForObject(url, String.class);
                        if (response != null) {
                            JsonNode petsArray = objectMapper.readTree(response);
                            if (petsArray.isArray()) {
                                for (JsonNode petNode : petsArray) {
                                    if (petNode.has("name") && petName.equalsIgnoreCase(petNode.get("name").asText())) {
                                        // Add tag "found-in-petstore" if not already present
                                        ArrayNode tagsArray = (ArrayNode) entity.get("tags");
                                        boolean tagExists = false;
                                        for (JsonNode tagNode : tagsArray) {
                                            if ("found-in-petstore".equals(tagNode.asText())) {
                                                tagExists = true;
                                                break;
                                            }
                                        }
                                        if (!tagExists) {
                                            tagsArray.add("found-in-petstore");
                                            logger.info("Added tag 'found-in-petstore' to entity");
                                        }
                                        break; // Found, stop search
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to enrich pet entity asynchronously", e);
                        // Do not block persistence on failure
                    }
                    return entity;
                });
            }
        }

        // No async enrichment needed or no name provided; return completed future immediately
        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: type={}, status={}, tags={}",
                request.getType(), request.getStatus(), request.getTags());

        String statusQuery = (request.getStatus() != null && !request.getStatus().trim().isEmpty()) ? request.getStatus().trim() : "available";
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusQuery;
        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(rawResponse);
            List<Pet> filteredPets = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode petNode : root) {
                    UUID technicalId = null;
                    if (petNode.has("id")) {
                        try {
                            // Create UUID deterministically from id string bytes
                            technicalId = UUID.nameUUIDFromBytes(petNode.get("id").asText().getBytes());
                        } catch (Exception ignored) {}
                    }
                    String name = petNode.has("name") ? petNode.get("name").asText() : null;
                    String type = null;
                    if (petNode.has("category") && petNode.get("category").has("name")) {
                        type = petNode.get("category").get("name").asText();
                    }
                    String status = petNode.has("status") ? petNode.get("status").asText() : null;
                    List<String> tags = new ArrayList<>();
                    if (petNode.has("tags") && petNode.get("tags").isArray()) {
                        for (JsonNode tagNode : petNode.get("tags")) {
                            if (tagNode.has("name")) tags.add(tagNode.get("name").asText());
                        }
                    }
                    Pet pet = new Pet(technicalId, name, type, status, tags);

                    boolean matchesType = (request.getType() == null || request.getType().trim().isEmpty() || request.getType().equalsIgnoreCase(type));
                    boolean matchesTags = true;
                    if (request.getTags() != null && !request.getTags().isEmpty()) {
                        matchesTags = request.getTags().stream().allMatch(tags::contains);
                    }
                    if (matchesType && matchesTags) {
                        filteredPets.add(pet);
                    }
                }
            }
            logger.info("Returning {} pets after filtering", filteredPets.size());
            return new SearchResponse(filteredPets);
        } catch (Exception e) {
            logger.error("Error fetching or processing pets from Petstore API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddPetResponse addPet(@RequestBody @Valid AddPetRequest request) {
        logger.info("Adding new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());

        // Convert AddPetRequest to ObjectNode for workflow processing
        ObjectNode petNode = objectMapper.valueToTree(request);

        // Call addItem with workflow processPet to handle async processing and entity mutation
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet);

        UUID technicalId = idFuture.join();
        logger.info("Pet added with technicalId={}", technicalId);
        return new AddPetResponse(technicalId, "Pet added successfully");
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") @NotNull UUID id) {
        logger.info("Fetching pet details for technicalId={}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.join();
        if (itemNode == null || itemNode.isEmpty()) {
            logger.error("Pet technicalId={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        try {
            Pet pet = objectMapper.treeToValue(itemNode, Pet.class);
            pet.setTechnicalId(id); // ensure technicalId is set
            return pet;
        } catch (Exception e) {
            logger.error("Failed to deserialize pet entity for id={}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse pet entity");
        }
    }

    @PostMapping(value = "/{id}/update", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse updatePet(@PathVariable("id") @NotNull UUID id, @RequestBody @Valid UpdatePetRequest request) {
        logger.info("Updating pet technicalId={}", id);

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.join();
        if (itemNode == null || itemNode.isEmpty()) {
            logger.error("Pet technicalId={} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Merge update request fields into existing entity ObjectNode
        if (request.getName() != null) {
            itemNode.put("name", request.getName());
        }
        if (request.getType() != null) {
            itemNode.put("type", request.getType());
        }
        if (request.getStatus() != null) {
            itemNode.put("status", request.getStatus());
        }
        if (request.getTags() != null) {
            ArrayNode tagsArray = itemNode.putArray("tags");
            for (String tag : request.getTags()) {
                if (tag != null && !tag.trim().isEmpty()) {
                    tagsArray.add(tag.trim());
                }
            }
        }

        // Call updateItem with workflow function to process entity before persistence
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, itemNode, this::processPet);
        updatedIdFuture.join();

        logger.info("Pet technicalId={} updated successfully", id);
        return new MessageResponse("Pet updated successfully");
    }

    @PostMapping(value = "/{id}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse deletePet(@PathVariable("id") @NotNull UUID id) {
        logger.info("Deleting pet technicalId={}", id);
        CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
        UUID deletedId = deletedIdFuture.join();
        if (deletedId == null) {
            logger.error("Pet technicalId={} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        logger.info("Pet technicalId={} deleted successfully", id);
        return new MessageResponse("Pet deleted successfully");
    }
}