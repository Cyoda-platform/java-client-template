```java
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory store for pets keyed by id
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private long nextId = 1L;

    // === DTOs ===

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
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
        private String name;
        private String type;
        private String status;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UpdatePetRequest {
        private String name;
        private String type;
        private String status;
        private List<String> tags;
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

    // === Endpoints ===

    /**
     * Search pets: POST /pets/search
     * Fetches pets from Petstore API by criteria, filters locally, returns results.
     */
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody SearchRequest request) {
        logger.info("Received search request: type={}, status={}, tags={}",
                request.getType(), request.getStatus(), request.getTags());

        // Call external Petstore API to get all pets by status if provided, else get all available pets (default)
        // Petstore API supports /pet/findByStatus?status=available,sold,pending
        String statusQuery = request.getStatus() != null ? request.getStatus() : "available";
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusQuery;

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(rawResponse);

            List<Pet> filteredPets = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode petNode : root) {
                    // Map fields from Petstore API response to our Pet object
                    Long id = petNode.has("id") ? petNode.get("id").asLong() : null;
                    String name = petNode.has("name") ? petNode.get("name").asText() : null;

                    // type is from category.name if exists
                    String type = null;
                    if (petNode.has("category") && petNode.get("category").has("name")) {
                        type = petNode.get("category").get("name").asText();
                    }

                    String status = petNode.has("status") ? petNode.get("status").asText() : null;

                    // tags is a list of tag.name
                    List<String> tags = new ArrayList<>();
                    if (petNode.has("tags") && petNode.get("tags").isArray()) {
                        for (JsonNode tagNode : petNode.get("tags")) {
                            if (tagNode.has("name")) tags.add(tagNode.get("name").asText());
                        }
                    }

                    Pet pet = new Pet(id, name, type, status, tags);

                    // Filter locally by type and tags if provided
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

    /**
     * Add new pet: POST /pets
     * Adds pet to local store and asynchronously attempts to sync with Petstore API (TODO).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddPetResponse addPet(@RequestBody AddPetRequest request) {
        logger.info("Adding new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());

        long id = generateNextId();
        Pet pet = new Pet(id, request.getName(), request.getType(), request.getStatus(), 
                request.getTags() != null ? request.getTags() : Collections.emptyList());
        petStore.put(id, pet);

        // Fire-and-forget sync with Petstore API (TODO)
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

    /**
     * Get pet details by ID: GET /pets/{id}
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") Long id) {
        logger.info("Fetching pet details for id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    /**
     * Update pet: POST /pets/{id}/update
     * Updates local store and asynchronously attempts to sync with Petstore API (TODO).
     */
    @PostMapping(value = "/{id}/update", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse updatePet(@PathVariable("id") Long id, @RequestBody UpdatePetRequest request) {
        logger.info("Updating pet id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet id={} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Update fields if present
        if (request.getName() != null) pet.setName(request.getName());
        if (request.getType() != null) pet.setType(request.getType());
        if (request.getStatus() != null) pet.setStatus(request.getStatus());
        if (request.getTags() != null) pet.setTags(request.getTags());

        petStore.put(id, pet);

        // Fire-and-forget sync with Petstore API (TODO)
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

    /**
     * Delete pet: POST /pets/{id}/delete
     * Deletes from local store and asynchronously attempts to delete from Petstore API (TODO).
     */
    @PostMapping(value = "/{id}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse deletePet(@PathVariable("id") Long id) {
        logger.info("Deleting pet id={}", id);
        Pet pet = petStore.remove(id);
        if (pet == null) {
            logger.error("Pet id={} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Fire-and-forget sync with Petstore API (TODO)
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

    // === Helpers ===

    private synchronized long generateNextId() {
        return nextId++;
    }

    // === Basic error handling ===

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
```
