```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache last fetched pets JSON as JsonNode (simulate cache)
    private volatile JsonNode cachedPets = null;

    // Simple in-memory favorites store (petId -> PetFavorite)
    private final Map<Long, PetFavorite> favorites = new ConcurrentHashMap<>();

    @PostMapping("/fetch")
    public ResponseEntity<PetsResponse> fetchPets(@RequestBody PetFetchRequest request) {
        logger.info("Received fetchPets request with filters: status={}, tags={}", request.getStatus(), request.getTags());

        try {
            // Build URL for Petstore API for "findByStatus"
            String url = PETSTORE_API_BASE + "/findByStatus?status=" + (request.getStatus() != null ? request.getStatus() : "available");

            JsonNode responseNode = restTemplate.getForObject(url, JsonNode.class);
            if (responseNode == null || !responseNode.isArray()) {
                logger.error("Unexpected response from Petstore API: not an array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external Petstore API");
            }

            // Filter by tags if provided
            List<JsonNode> filteredPets = objectMapper.convertValue(responseNode, List.class).stream()
                    .map(objectMapper::convertValue)
                    .map(node -> (JsonNode) node)
                    .collect(Collectors.toList());

            // We use JsonNode list directly for filtering to keep prototype simple
            List<JsonNode> filtered = responseNode.findValuesAsText("tags").isEmpty() && (request.getTags() == null || request.getTags().isEmpty())
                    ? responseNode.findValues("") // no tags filter, use all
                    : filterByTags(responseNode, request.getTags());

            // Store filtered pets into cachedPets for GET /pets endpoint
            cachedPets = objectMapper.valueToTree(filtered);

            // Map filtered pets into Pet DTO list for response
            List<Pet> pets = filtered.stream()
                    .map(this::jsonNodeToPet)
                    .collect(Collectors.toList());

            logger.info("Fetched and filtered {} pets", pets.size());
            return ResponseEntity.ok(new PetsResponse(pets));

        } catch (Exception e) {
            logger.error("Error fetching pets from Petstore API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getCachedPets() {
        logger.info("Retrieving cached pets");
        if (cachedPets == null) {
            logger.info("No cached pets available");
            return ResponseEntity.ok(new PetsResponse(List.of()));
        }
        try {
            List<Pet> pets = objectMapper.convertValue(cachedPets, List.class).stream()
                    .map(objectMapper::convertValue)
                    .map(node -> (JsonNode) node)
                    .map(this::jsonNodeToPet)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(new PetsResponse(pets));
        } catch (Exception e) {
            logger.error("Error reading cached pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read cached pets");
        }
    }

    @PostMapping("/favorites/add")
    public ResponseEntity<FavoriteResponse> addFavorite(@RequestBody FavoriteRequest request) {
        logger.info("Add favorite request for petId={}", request.getPetId());

        if (request.getPetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId is required");
        }
        // Check if pet exists in cachedPets
        if (cachedPets == null) {
            logger.error("No cached pets to validate petId");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pets data available, fetch first");
        }
        boolean exists = false;
        for (JsonNode petNode : cachedPets) {
            if (petNode.has("id") && petNode.get("id").asLong() == request.getPetId()) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            logger.error("Pet id {} not found in cached pets", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet with given id not found");
        }

        // Add to favorites (overwrite if exists)
        favorites.put(request.getPetId(), new PetFavorite(request.getPetId(), Instant.now()));
        logger.info("Pet id {} added to favorites", request.getPetId());

        return ResponseEntity.ok(new FavoriteResponse("Pet added to favorites", request.getPetId()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<FavoritesListResponse> getFavorites() {
        logger.info("Retrieving favorite pets");
        if (favorites.isEmpty()) {
            return ResponseEntity.ok(new FavoritesListResponse(List.of()));
        }
        if (cachedPets == null) {
            logger.error("No cached pets available for favorites lookup");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pets data available, fetch first");
        }

        List<Pet> favoritePets = favorites.keySet().stream()
                .map(petId -> findPetInCached(petId))
                .filter(pet -> pet != null)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new FavoritesListResponse(favoritePets));
    }

    private Pet findPetInCached(Long petId) {
        for (JsonNode petNode : cachedPets) {
            if (petNode.has("id") && petNode.get("id").asLong() == petId) {
                return jsonNodeToPet(petNode);
            }
        }
        return null;
    }

    private Pet jsonNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        pet.setId(node.has("id") ? node.get("id").asLong() : null);
        pet.setName(node.has("name") ? node.get("name").asText() : null);
        pet.setStatus(node.has("status") ? node.get("status").asText() : null);
        if (node.has("category") && node.get("category").has("name")) {
            pet.setCategory(node.get("category").get("name").asText());
        }
        if (node.has("tags") && node.get("tags").isArray()) {
            List<String> tags = objectMapper.convertValue(node.get("tags"), List.class).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            pet.setTags(tags);
        }
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            List<String> photos = objectMapper.convertValue(node.get("photoUrls"), List.class).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            pet.setPhotoUrls(photos);
        }
        return pet;
    }

    private List<JsonNode> filterByTags(JsonNode petsArray, List<String> tags) {
        if (tags == null || tags.isEmpty()) return petsArray.findValues("");
        return petsArray.findParents("").stream()
                .filter(petNode -> {
                    if (!petNode.has("tags")) return false;
                    for (JsonNode tagNode : petNode.get("tags")) {
                        if (tagNode.has("name") && tags.contains(tagNode.get("name").asText())) {
                            return true;
                        }
                    }
                    return false;
                }).collect(Collectors.toList());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // DTOs

    @Data
    public static class PetFetchRequest {
        private String status; // available|pending|sold
        private List<String> tags;
    }

    @Data
    @AllArgsConstructor
    public static class PetsResponse {
        private List<Pet> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status;
        private List<String> tags;
        private List<String> photoUrls;
    }

    @Data
    public static class FavoriteRequest {
        private Long petId;
    }

    @Data
    @AllArgsConstructor
    public static class FavoriteResponse {
        private String message;
        private Long petId;
    }

    @Data
    @AllArgsConstructor
    public static class FavoritesListResponse {
        private List<Pet> favorites;
    }

    @Data
    @AllArgsConstructor
    public static class PetFavorite {
        private Long petId;
        private Instant addedAt;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
```