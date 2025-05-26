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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory cache for last search results
    private final Map<String, Pet[]> lastSearchCache = new ConcurrentHashMap<>();

    // In-memory user favorites storage (keyed by userId for prototype simplicity use "defaultUser")
    private final Map<String, Map<Long, Pet>> userFavorites = new ConcurrentHashMap<>();

    private static final String DEFAULT_USER = "defaultUser";

    // === Data Models ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String type;   // mapped from "category.name" or "category"
        private String status;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    static class SearchRequest {
        private String type;
        private String status;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    static class FavoriteRequest {
        private Long petId;
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }

    // === Endpoints ===

    /**
     * POST /pets/search
     * Searches pets by criteria by querying external Petstore API.
     * Stores last search results internally for GET /pets retrieval.
     */
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Pet[]>> searchPets(@RequestBody SearchRequest request) {
        logger.info("Received search request: type={}, status={}, tags={}",
                request.getType(), request.getStatus(), request.getTags());

        try {
            // Petstore API provides GET /pet/findByStatus or /pet/findByTags endpoints.
            // We combine criteria here by calling findByStatus and filtering by type and tags in app.

            // If status is missing, default to "available" for search
            String statusParam = (request.getStatus() == null || request.getStatus().isBlank())
                    ? "available" : request.getStatus();

            // Fetch pets by status from Petstore API
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;
            logger.info("Querying external Petstore API: {}", url);

            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unexpected response format from Petstore API");
            }

            List<Pet> pets = new ArrayList<>();
            for (JsonNode petNode : root) {
                Pet pet = parsePetFromJsonNode(petNode);
                if (pet == null) continue;

                // Filter by type if specified
                if (request.getType() != null && !request.getType().isBlank()) {
                    if (!request.getType().equalsIgnoreCase(pet.getType())) {
                        continue;
                    }
                }

                // Filter by tags if specified
                if (request.getTags() != null && !request.getTags().isEmpty()) {
                    if (pet.getTags() == null || !pet.getTags().containsAll(request.getTags())) {
                        continue;
                    }
                }

                pets.add(pet);
            }

            Pet[] petsArray = pets.toArray(new Pet[0]);
            lastSearchCache.put(DEFAULT_USER, petsArray);

            Map<String, Pet[]> resp = new HashMap<>();
            resp.put("pets", petsArray);

            logger.info("Returning {} pets from search", petsArray.length);
            return ResponseEntity.ok(resp);

        } catch (Exception ex) {
            logger.error("Error during searchPets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    /**
     * GET /pets
     * Returns cached last search results for the user.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Pet[]>> getLastSearchPets() {
        Pet[] pets = lastSearchCache.getOrDefault(DEFAULT_USER, new Pet[0]);
        Map<String, Pet[]> resp = new HashMap<>();
        resp.put("pets", pets);
        logger.info("Returning {} cached pets for last search", pets.length);
        return ResponseEntity.ok(resp);
    }

    /**
     * POST /pets/favorite
     * Adds a pet to user's favorites. Uses internal storage.
     */
    @PostMapping(value = "/favorite", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> addFavorite(@RequestBody FavoriteRequest request) {
        logger.info("Adding petId={} to favorites for user={}", request.getPetId(), DEFAULT_USER);

        if (request.getPetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId is required");
        }

        // Retrieve last search pets to validate petId
        Pet[] cachedPets = lastSearchCache.get(DEFAULT_USER);
        if (cachedPets == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pets available to favorite");
        }

        Optional<Pet> petToFavorite = Arrays.stream(cachedPets)
                .filter(p -> p.getId().equals(request.getPetId()))
                .findFirst();

        if (petToFavorite.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet with given ID not found in last search");
        }

        userFavorites.computeIfAbsent(DEFAULT_USER, k -> new ConcurrentHashMap<>())
                .put(request.getPetId(), petToFavorite.get());

        logger.info("Pet {} added to favorites", request.getPetId());

        // TODO: In a full implementation, persist favorites per user

        return ResponseEntity.ok(new MessageResponse("Pet added to favorites"));
    }

    /**
     * GET /pets/favorites
     * Returns user's favorite pets from internal storage.
     */
    @GetMapping(value = "/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Collection<Pet>>> getFavorites() {
        Collection<Pet> favorites = userFavorites.getOrDefault(DEFAULT_USER, Collections.emptyMap()).values();
        Map<String, Collection<Pet>> resp = new HashMap<>();
        resp.put("favorites", favorites);
        logger.info("Returning {} favorite pets for user", favorites.size());
        return ResponseEntity.ok(resp);
    }

    // === Helpers ===

    private Pet parsePetFromJsonNode(JsonNode petNode) {
        try {
            Long id = petNode.has("id") && !petNode.get("id").isNull() ? petNode.get("id").asLong() : null;
            String name = petNode.has("name") && !petNode.get("name").isNull() ? petNode.get("name").asText() : null;
            String status = petNode.has("status") && !petNode.get("status").isNull() ? petNode.get("status").asText() : null;

            // type from category.name or category object
            String type = null;
            if (petNode.has("category") && !petNode.get("category").isNull()) {
                JsonNode categoryNode = petNode.get("category");
                if (categoryNode.has("name") && !categoryNode.get("name").isNull()) {
                    type = categoryNode.get("name").asText();
                } else if (categoryNode.isTextual()) {
                    type = categoryNode.asText();
                }
            }

            List<String> tags = new ArrayList<>();
            if (petNode.has("tags") && petNode.get("tags").isArray()) {
                for (JsonNode tagNode : petNode.get("tags")) {
                    if (tagNode.has("name") && !tagNode.get("name").isNull()) {
                        tags.add(tagNode.get("name").asText());
                    }
                }
            }

            return new Pet(id, name, type, status, tags);

        } catch (Exception e) {
            logger.error("Failed to parse pet from JSON node", e);
            return null;
        }
    }

    // === Minimal exception handling ===

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal server error");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```
