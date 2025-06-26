```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage for favorites and matched pets (mock persistence)
    private final Map<Long, Pet> favorites = new ConcurrentHashMap<>();
    private final Map<Long, Pet> petStorage = new ConcurrentHashMap<>();

    /**
     * POST /pets/search
     * Retrieves and processes pet data from external Petstore API based on search criteria.
     */
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResponse> searchPets(@RequestBody SearchRequest request) {
        logger.info("Received searchPets request: {}", request);

        try {
            // Build Petstore API URL with query params (simple mock)
            // TODO: Petstore API real endpoint: https://petstore.swagger.io/v2/pet/findByStatus?status=available
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + 
                (StringUtils.hasText(request.getStatus()) ? request.getStatus() : "available");

            JsonNode responseJson = restTemplate.getForObject(url, JsonNode.class);
            if (responseJson == null || !responseJson.isArray()) {
                logger.error("Unexpected response from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }

            List<Pet> results = new ArrayList<>();
            for (JsonNode node : responseJson) {
                long id = node.path("id").asLong();
                String name = node.path("name").asText(null);
                String status = node.path("status").asText(null);
                String type = extractCategoryName(node.path("category"));
                String description = node.path("description").asText(null);

                // Filter by type if specified and not "all"
                if (request.getType() != null && !"all".equalsIgnoreCase(request.getType())) {
                    if (!request.getType().equalsIgnoreCase(type)) {
                        continue;
                    }
                }

                // Filter by name if specified
                if (request.getName() != null && !request.getName().isBlank()) {
                    if (name == null || !name.toLowerCase().contains(request.getName().toLowerCase())) {
                        continue;
                    }
                }

                Pet pet = new Pet(id, name, type, status, description, null, null);
                results.add(pet);

                // Cache in petStorage for GET retrieval later
                petStorage.put(id, pet);
            }

            logger.info("searchPets returning {} results", results.size());
            return ResponseEntity.ok(new SearchResponse(results));

        } catch (ResponseStatusException ex) {
            logger.error("Error during searchPets: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unhandled error during searchPets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * POST /pets/match
     * Runs a fun matching algorithm based on user preferences.
     */
    @PostMapping(value = "/match", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchResponse> matchPets(@RequestBody MatchRequest request) {
        logger.info("Received matchPets request: {}", request);

        try {
            // For prototype, fetch pets from external API by status "available"
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            JsonNode responseJson = restTemplate.getForObject(url, JsonNode.class);
            if (responseJson == null || !responseJson.isArray()) {
                logger.error("Unexpected response from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }

            List<Pet> allPets = new ArrayList<>();
            for (JsonNode node : responseJson) {
                long id = node.path("id").asLong();
                String name = node.path("name").asText(null);
                String status = node.path("status").asText(null);
                String type = extractCategoryName(node.path("category"));
                Integer age = node.path("age").isInt() ? node.path("age").asInt() : null; // Petstore API may not have age, so mock below
                Boolean friendly = null; // No data from Petstore API, mock below
                String description = node.path("description").asText(null);

                // For prototype, mock age and friendly if missing
                if (age == null) {
                    age = 1 + new Random().nextInt(10); // random age 1-10
                }
                if (friendly == null) {
                    friendly = new Random().nextBoolean();
                }

                Pet pet = new Pet(id, name, type, status, description, age, friendly);
                allPets.add(pet);

                // Cache in petStorage
                petStorage.put(id, pet);
            }

            // Apply matching filters
            List<Pet> matches = new ArrayList<>();
            for (Pet pet : allPets) {
                if (request.getPreferences() == null) {
                    break; // no preferences, skip matching
                }
                if (request.getPreferences().getType() != null &&
                        !request.getPreferences().getType().equalsIgnoreCase(pet.getType())) {
                    continue;
                }
                if (request.getPreferences().getAgeRange() != null) {
                    int min = request.getPreferences().getAgeRange().getMin();
                    int max = request.getPreferences().getAgeRange().getMax();
                    if (pet.getAge() == null || pet.getAge() < min || pet.getAge() > max) {
                        continue;
                    }
                }
                if (request.getPreferences().getFriendly() != null) {
                    if (!request.getPreferences().getFriendly().equals(pet.getFriendly())) {
                        continue;
                    }
                }
                matches.add(pet);
            }

            logger.info("matchPets returning {} matches", matches.size());
            return ResponseEntity.ok(new MatchResponse(matches));

        } catch (ResponseStatusException ex) {
            logger.error("Error during matchPets: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unhandled error during matchPets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * GET /pets/{id}
     * Retrieve details of a specific pet stored or previously matched.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pet> getPetById(@PathVariable("id") Long id) {
        logger.info("Received getPetById request for id={}", id);
        Pet pet = petStorage.get(id);
        if (pet == null) {
            logger.error("Pet with id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * GET /pets/favorites
     * Retrieve user's favorite pets.
     */
    @GetMapping(value = "/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FavoritesResponse> getFavorites() {
        logger.info("Received getFavorites request");
        List<PetSummary> favList = new ArrayList<>();
        favorites.values().forEach(pet -> favList.add(new PetSummary(pet.getId(), pet.getName(), pet.getType())));
        return ResponseEntity.ok(new FavoritesResponse(favList));
    }

    /**
     * POST /pets/favorites
     * Add or remove a pet from favorites.
     */
    @PostMapping(value = "/favorites", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ActionResponse> modifyFavorites(@RequestBody FavoriteRequest request) {
        logger.info("Received modifyFavorites request: {}", request);

        if (request.getPetId() == null || request.getAction() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId and action must be provided");
        }

        Pet pet = petStorage.get(request.getPetId());
        if (pet == null) {
            logger.error("Pet with id={} not found for favorite action", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        if ("add".equalsIgnoreCase(request.getAction())) {
            favorites.put(pet.getId(), pet);
            logger.info("Pet id={} added to favorites", pet.getId());
            return ResponseEntity.ok(new ActionResponse("success", "Pet added to favorites"));
        } else if ("remove".equalsIgnoreCase(request.getAction())) {
            favorites.remove(pet.getId());
            logger.info("Pet id={} removed from favorites", pet.getId());
            return ResponseEntity.ok(new ActionResponse("success", "Pet removed from favorites"));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action, must be add or remove");
        }
    }

    // Utility to extract category name safely
    private String extractCategoryName(JsonNode categoryNode) {
        if (categoryNode != null && categoryNode.has("name")) {
            return categoryNode.get("name").asText();
        }
        return null;
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String type; // cat, dog, all
        private String status; // available, pending, sold
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private List<Pet> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchRequest {
        private Preferences preferences;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Preferences {
        private String type; // cat or dog
        private AgeRange ageRange;
        private Boolean friendly;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgeRange {
        private int min;
        private int max;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResponse {
        private List<Pet> matches;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String description;
        private Integer age;      // nullable
        private Boolean friendly; // nullable
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoritesResponse {
        private List<PetSummary> favorites;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetSummary {
        private Long id;
        private String name;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoriteRequest {
        private Long petId;
        private String action; // add or remove
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionResponse {
        private String status;
        private String message;
    }

    // --- Minimal exception handler ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        logger.error("Handling exception: {}", errorBody);
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

}
```