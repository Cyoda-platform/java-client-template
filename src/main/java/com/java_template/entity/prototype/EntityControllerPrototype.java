package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/purrfect-pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory cache for pet details keyed by pet id
    private final Map<Long, Pet> petCache = new ConcurrentHashMap<>();

    // --- Models ---

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
        private List<String> tags;
    }

    @Data
    public static class SearchRequest {
        private String type; // cat|dog|bird|all
        private String status; // available|pending|sold
        private String name; // optional
    }

    @Data
    public static class SearchResponse {
        private List<Pet> pets;
    }

    @Data
    public static class RecommendationsRequest {
        @NotBlank
        private String preferredType;
        @NotBlank
        private String preferredStatus;
    }

    @Data
    public static class RecommendationsResponse {
        private List<Pet> recommendedPets;
    }

    // --- API Endpoints ---

    /**
     * POST /pets/search
     * Searches pets with filters by invoking external Petstore API.
     */
    @PostMapping(path = "/pets/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody SearchRequest request) {
        logger.info("Received searchPets request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());

        try {
            // Build URL with query parameters for Petstore API findByStatus
            // Petstore supports findByStatus?status=available,pending,sold
            // For type filtering, we filter results in-memory as Petstore does not support type filtering directly
            String url = PETSTORE_BASE_URL + "/pet/findByStatus?status=" + Optional.ofNullable(request.getStatus()).orElse("available");

            String rawJson = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawJson);

            List<Pet> filteredPets = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = parsePetFromJsonNode(petNode);
                    if (pet == null) continue;

                    // Filter by type if provided and not "all"
                    if (request.getType() != null && !"all".equalsIgnoreCase(request.getType()) && !request.getType().equalsIgnoreCase(pet.getType())) {
                        continue;
                    }

                    // Filter by name if provided
                    if (request.getName() != null && !request.getName().isBlank() && !pet.getName().toLowerCase().contains(request.getName().toLowerCase())) {
                        continue;
                    }

                    filteredPets.add(pet);

                    // Add to cache
                    petCache.put(pet.getId(), pet);
                }
            }

            logger.info("searchPets found {} pets matching criteria", filteredPets.size());

            SearchResponse response = new SearchResponse();
            response.setPets(filteredPets);
            return response;

        } catch (Exception e) {
            logger.error("Error during searchPets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    /**
     * GET /pets/{id}
     * Retrieves detailed pet info stored in the app cache.
     */
    @GetMapping(path = "/pets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable Long id) {
        logger.info("Fetching pet details for id={}", id);
        Pet pet = petCache.get(id);
        if (pet == null) {
            logger.warn("Pet with id={} not found in cache", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    /**
     * POST /pets/recommendations
     * Provides fun pet recommendations based on user preferences by invoking external Petstore API.
     */
    @PostMapping(path = "/pets/recommendations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public RecommendationsResponse getRecommendations(@RequestBody RecommendationsRequest request) {
        logger.info("Received getRecommendations request: preferredType={}, preferredStatus={}", request.getPreferredType(), request.getPreferredStatus());

        try {
            // Petstore API only supports findByStatus - we invoke that and filter by type in-memory
            String url = PETSTORE_BASE_URL + "/pet/findByStatus?status=" + request.getPreferredStatus();

            String rawJson = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawJson);

            List<Pet> recommended = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = parsePetFromJsonNode(petNode);
                    if (pet == null) continue;

                    if ("all".equalsIgnoreCase(request.getPreferredType()) || request.getPreferredType().equalsIgnoreCase(pet.getType())) {
                        recommended.add(pet);

                        // Cache for subsequent GET calls
                        petCache.put(pet.getId(), pet);
                    }
                }
            }

            // TODO: Add more fun recommendation logic here (e.g., ranking, random picks)

            RecommendationsResponse response = new RecommendationsResponse();
            response.setRecommendedPets(recommended);
            return response;

        } catch (Exception e) {
            logger.error("Error during getRecommendations", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get recommendations");
        }
    }

    // --- Helper Methods ---

    private Pet parsePetFromJsonNode(JsonNode node) {
        try {
            Pet pet = new Pet();
            pet.setId(node.path("id").asLong());
            pet.setName(node.path("name").asText(null));
            pet.setStatus(node.path("status").asText(null));

            // Determine type from category.name if present
            JsonNode categoryNode = node.path("category");
            if (categoryNode.isObject()) {
                pet.setType(categoryNode.path("name").asText(null));
            } else {
                pet.setType(null);
            }

            // photoUrls array
            List<String> photos = new ArrayList<>();
            JsonNode photosNode = node.path("photoUrls");
            if (photosNode.isArray()) {
                for (JsonNode photoUrl : photosNode) {
                    photos.add(photoUrl.asText());
                }
            }
            pet.setPhotoUrls(photos);

            // tags array (names only)
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = node.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tagNode : tagsNode) {
                    String tagName = tagNode.path("name").asText(null);
                    if (tagName != null) {
                        tags.add(tagName);
                    }
                }
            }
            pet.setTags(tags);

            return pet;
        } catch (Exception e) {
            logger.error("Failed to parse Pet from JSON node", e);
            return null;
        }
    }

    // --- Minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return error;
    }

}