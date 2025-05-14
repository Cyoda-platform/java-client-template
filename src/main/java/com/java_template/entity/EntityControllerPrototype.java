package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private long nextId = 1L;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SearchRequest {
        private String type;
        private String status;
        private List<@NotBlank String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
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
    static class UpdatePetRequest {
        private String name;
        private String type;
        private String status;
        private List<@NotBlank String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetResponse {
        private Long id;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SearchResponse {
        private List<Pet> pets;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: type={}, status={}, tags={}",
                request.getType(), request.getStatus(), request.getTags());
        String statusQuery = request.getStatus() != null ? request.getStatus() : "available";
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusQuery;
        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(rawResponse);
            List<Pet> filteredPets = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode petNode : root) {
                    Long id = petNode.has("id") ? petNode.get("id").asLong() : null;
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
                    Pet pet = new Pet(id, name, type, status, tags);
                    boolean matchesType = (request.getType() == null || request.getType().equalsIgnoreCase(type));
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
        long id = generateNextId();
        Pet pet = new Pet(id, request.getName(), request.getType(), request.getStatus(),
                request.getTags() != null ? request.getTags() : Collections.emptyList());
        petStore.put(id, pet);
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Syncing added pet id={} to Petstore API (mocked)", id);
                // TODO: Implement actual Petstore API POST /pet integration with JSON payload
            } catch (Exception ex) {
                logger.error("Failed to sync added pet id={} to Petstore API", id, ex);
            }
        });
        return new AddPetResponse(id, "Pet added successfully");
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") @NotNull Long id) {
        logger.info("Fetching pet details for id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @PostMapping(value = "/{id}/update", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse updatePet(@PathVariable("id") @NotNull Long id, @RequestBody @Valid UpdatePetRequest request) {
        logger.info("Updating pet id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet id={} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (request.getName() != null) pet.setName(request.getName());
        if (request.getType() != null) pet.setType(request.getType());
        if (request.getStatus() != null) pet.setStatus(request.getStatus());
        if (request.getTags() != null) pet.setTags(request.getTags());
        petStore.put(id, pet);
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Syncing updated pet id={} to Petstore API (mocked)", id);
                // TODO: Implement actual Petstore API PUT /pet integration with JSON payload
            } catch (Exception ex) {
                logger.error("Failed to sync updated pet id={} to Petstore API", id, ex);
            }
        });
        return new MessageResponse("Pet updated successfully");
    }

    @PostMapping(value = "/{id}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse deletePet(@PathVariable("id") @NotNull Long id) {
        logger.info("Deleting pet id={}", id);
        Pet pet = petStore.remove(id);
        if (pet == null) {
            logger.error("Pet id={} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Syncing delete pet id={} to Petstore API (mocked)", id);
                // TODO: Implement actual Petstore API DELETE /pet/{id} integration
            } catch (Exception ex) {
                logger.error("Failed to sync delete pet id={} to Petstore API", id, ex);
            }
        });
        return new MessageResponse("Pet deleted successfully");
    }

    private synchronized long generateNextId() {
        return nextId++;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneralException(Exception ex) {
        logger.error("Internal server error: ", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("status", 500);
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred");
        return error;
    }
}