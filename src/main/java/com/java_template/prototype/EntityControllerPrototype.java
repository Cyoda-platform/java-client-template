package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for cached pets indexed by petId
    private final Map<Long, Pet> petsCache = new ConcurrentHashMap<>();

    // In-memory store for pet categories (simple set)
    private final Set<String> categoriesCache = ConcurrentHashMap.newKeySet();

    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchPets(@RequestBody(required = false) FetchRequest request) {
        logger.info("Received fetch request with filters: {}", request == null ? "{}" : request);

        // Build URL for Petstore API call with optional filtering by status
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=available"; // default to available

        if (request != null && request.filters != null && request.filters.status != null) {
            url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + request.filters.status;
        }

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);

            if (!rootNode.isArray()) {
                // Defensive: Expecting an array of pets
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected response format");
            }

            int count = 0;
            petsCache.clear();
            categoriesCache.clear();

            for (JsonNode petNode : rootNode) {
                Pet pet = parsePetFromJsonNode(petNode);

                // If filters.tags present, filter pets accordingly
                if (request != null && request.filters != null && request.filters.tags != null && !request.filters.tags.isEmpty()) {
                    if (pet.getTags() == null || Collections.disjoint(pet.getTags(), request.filters.tags)) {
                        continue; // skip pet not matching tags
                    }
                }

                petsCache.put(pet.getId(), pet);
                if (pet.getCategory() != null && pet.getCategory().getName() != null) {
                    categoriesCache.add(pet.getCategory().getName());
                }
                count++;
            }

            logger.info("Fetched and cached {} pets", count);

            // TODO: Consider async processing if fetching becomes heavy
            return ResponseEntity.ok(new FetchResponse("Pets fetched and stored successfully", count));

        } catch (Exception ex) {
            logger.error("Error fetching pets from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getPets() {
        logger.info("Returning {} cached pets", petsCache.size());
        return ResponseEntity.ok(new ArrayList<>(petsCache.values()));
    }

    @PostMapping("/details")
    public ResponseEntity<Pet> getPetDetails(@RequestBody @NotNull PetDetailsRequest request) {
        logger.info("Received request for pet details: petId={}", request.getPetId());

        if (petsCache.containsKey(request.getPetId())) {
            // Return cached pet with possible enrichment (here just return as is)
            return ResponseEntity.ok(petsCache.get(request.getPetId()));
        }

        // Not found in cache - fetch from external API
        String url = PETSTORE_API_BASE + "/pet/" + request.getPetId();

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode petNode = objectMapper.readTree(rawResponse);

            if (petNode.has("id") && petNode.get("id").asLong() == request.getPetId()) {
                Pet pet = parsePetFromJsonNode(petNode);
                logger.info("Fetched pet details from external API for petId={}", request.getPetId());
                return ResponseEntity.ok(pet);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }

        } catch (ResponseStatusException ex) {
            logger.error("Pet not found or error occurred: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            logger.error("Error fetching pet details from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet details from external API");
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<Set<String>> getCategories() {
        logger.info("Returning categories: {}", categoriesCache);
        return ResponseEntity.ok(categoriesCache);
    }

    // Minimal error handler to return JSON error body
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={} message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse errorResponse = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    // Helper to parse Pet from JsonNode returned by Petstore API
    private Pet parsePetFromJsonNode(JsonNode petNode) {
        Pet pet = new Pet();

        pet.setId(petNode.path("id").asLong(0));
        pet.setName(petNode.path("name").asText(null));
        pet.setStatus(petNode.path("status").asText(null));

        // Parse category object
        JsonNode categoryNode = petNode.path("category");
        if (!categoryNode.isMissingNode() && categoryNode.has("name")) {
            Category cat = new Category();
            cat.setId(categoryNode.path("id").asLong(0));
            cat.setName(categoryNode.path("name").asText(null));
            pet.setCategory(cat);
        }

        // Parse tags array of objects with 'name' fields
        JsonNode tagsNode = petNode.path("tags");
        if (tagsNode.isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : tagsNode) {
                if (tagNode.has("name")) {
                    tags.add(tagNode.get("name").asText());
                }
            }
            pet.setTags(tags);
        }

        // Description is not in the Petstore API by default, mock a description here
        pet.setDescription("Description not available from Petstore API."); // TODO: Replace with real description if available

        return pet;
    }

    // Request and Response DTOs

    @Data
    public static class FetchRequest {
        private Filters filters;

        @Data
        public static class Filters {
            private String status; // e.g. "available", "pending", "sold"
            private List<String> tags;
        }
    }

    @Data
    public static class FetchResponse {
        private String message;
        private int count;

        public FetchResponse(String message, int count) {
            this.message = message;
            this.count = count;
        }
    }

    @Data
    public static class PetDetailsRequest {
        @NotNull
        private Long petId;
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String status;
        private Category category;
        private List<String> tags;
        private String description;
    }

    @Data
    public static class Category {
        private Long id;
        private String name;
    }
}