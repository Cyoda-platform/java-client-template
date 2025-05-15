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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // In-memory store for added pets, favorites etc.
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> userFavorites = new ConcurrentHashMap<>();
    private long petIdSequence = 1000L; // simple id generator

    // ========== Models ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status; // e.g. available, pending, sold
        private List<String> photoUrls;
    }

    @Data
    static class SearchRequest {
        private String status;
        private String category;
        private String nameContains;
    }

    @Data
    static class AddPetRequest {
        private String name;
        private String category;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    static class FavoriteRequest {
        private Long userId;
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }

    // ========== Endpoints ==========

    /**
     * Search Pets - POST /pets/search
     * Calls external petstore API, filters results according to criteria, returns filtered list.
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, List<Pet>>> searchPets(@RequestBody SearchRequest searchRequest) {
        logger.info("Received search request: {}", searchRequest);

        try {
            // Call external Swagger Petstore API to fetch all pets with status
            // Swagger Petstore example endpoint: https://petstore.swagger.io/v2/pet/findByStatus?status=available
            String statusParam = (searchRequest.getStatus() != null) ? searchRequest.getStatus() : "available";

            URI uri = URI.create("https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Failed to fetch pets from external API, status code: {}", response.statusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External API error");
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            List<Pet> filteredPets = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = parsePetFromJson(petNode);

                    // Filter by category if specified
                    if (searchRequest.getCategory() != null && !searchRequest.getCategory().isEmpty()) {
                        if (!searchRequest.getCategory().equalsIgnoreCase(pet.getCategory())) {
                            continue;
                        }
                    }

                    // Filter by nameContains if specified
                    if (searchRequest.getNameContains() != null && !searchRequest.getNameContains().isEmpty()) {
                        if (pet.getName() == null || !pet.getName().toLowerCase().contains(searchRequest.getNameContains().toLowerCase())) {
                            continue;
                        }
                    }

                    filteredPets.add(pet);
                }
            }

            Map<String, List<Pet>> result = new HashMap<>();
            result.put("pets", filteredPets);

            logger.info("Returning {} pets after filtering", filteredPets.size());
            return ResponseEntity.ok(result);

        } catch (IOException | InterruptedException e) {
            logger.error("Error while searching pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request");
        }
    }

    /**
     * Add New Pet - POST /pets
     * Adds pet to in-memory store.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addPet(@RequestBody AddPetRequest addPetRequest) {
        logger.info("Adding new pet: {}", addPetRequest);

        // Basic validation
        if (addPetRequest.getName() == null || addPetRequest.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet name is required");
        }

        Pet newPet = new Pet();
        synchronized (this) {
            newPet.setId(++petIdSequence);
        }
        newPet.setName(addPetRequest.getName());
        newPet.setCategory(addPetRequest.getCategory());
        newPet.setStatus(addPetRequest.getStatus() != null ? addPetRequest.getStatus() : "available");
        newPet.setPhotoUrls(addPetRequest.getPhotoUrls() != null ? addPetRequest.getPhotoUrls() : Collections.emptyList());

        petStore.put(newPet.getId(), newPet);

        logger.info("Pet added with ID {}", newPet.getId());

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", newPet.getId());
        resp.put("message", "Pet added successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * Get Pet Details - GET /pets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long id) {
        logger.info("Fetching pet details for ID {}", id);

        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet not found with ID {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * Mark Pet as Favorite - POST /pets/{id}/favorite
     */
    @PostMapping("/{id}/favorite")
    public ResponseEntity<MessageResponse> markFavorite(@PathVariable Long id, @RequestBody FavoriteRequest favoriteRequest) {
        logger.info("Marking pet ID {} as favorite for user ID {}", id, favoriteRequest.getUserId());

        if (!petStore.containsKey(id)) {
            logger.error("Pet not found for favorite marking with ID {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (favoriteRequest.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UserId is required");
        }

        userFavorites.computeIfAbsent(favoriteRequest.getUserId(), k -> ConcurrentHashMap.newKeySet()).add(id);

        logger.info("Pet ID {} marked as favorite for user ID {}", id, favoriteRequest.getUserId());

        return ResponseEntity.ok(new MessageResponse("Pet marked as favorite"));
    }

    // ========== Helpers ==========

    private Pet parsePetFromJson(JsonNode petNode) {
        Pet pet = new Pet();
        pet.setId(petNode.path("id").asLong());
        pet.setName(petNode.path("name").asText(null));

        // Category is nested: petNode.category.name
        JsonNode categoryNode = petNode.path("category");
        if (categoryNode.isObject()) {
            pet.setCategory(categoryNode.path("name").asText(null));
        } else {
            pet.setCategory(null);
        }

        pet.setStatus(petNode.path("status").asText(null));

        List<String> photos = new ArrayList<>();
        JsonNode photosNode = petNode.path("photoUrls");
        if (photosNode.isArray()) {
            for (JsonNode photoUrlNode : photosNode) {
                photos.add(photoUrlNode.asText());
            }
        }
        pet.setPhotoUrls(photos);

        return pet;
    }

    // ========== Exception Handling ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Handling generic exception", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

}
```