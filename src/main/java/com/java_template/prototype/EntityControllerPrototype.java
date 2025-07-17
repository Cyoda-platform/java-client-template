package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final WebClient webClient = WebClient.create("https://petstore.swagger.io/v2");

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for user favorites: userId -> Set of petIds
    private final Map<String, Set<Integer>> userFavorites = new ConcurrentHashMap<>();

    // -- DTOs --

    @Data
    public static class PetSearchRequest {
        private String type;
        private String status;
        private String name;
    }

    @Data
    public static class AddFavoriteRequest {
        @NotBlank
        private String userId;
        private Integer petId;
    }

    @Data
    public static class PetDetailsRequest {
        private Integer petId;
    }

    // -- Endpoints --

    /**
     * POST /prototype/pets/search
     * Searches pets by criteria (type, status, name) by calling external Petstore API and filtering results.
     */
    @PostMapping("/search")
    public List<JsonNode> searchPets(@RequestBody PetSearchRequest request) {
        logger.info("Received search request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());

        // Call external Petstore API to get all pets by status (if provided) or all available
        // Petstore API: GET /pet/findByStatus?status=available
        // Since external calls should be in POST endpoint, we do the call here.
        String statusParam = request.getStatus() != null ? request.getStatus() : "available";

        try {
            Mono<String> responseMono = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/pet/findByStatus")
                            .queryParam("status", statusParam)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class);

            String responseJson = responseMono.block(); // blocking call in prototype
            JsonNode petsArray = objectMapper.readTree(responseJson);

            List<JsonNode> filteredPets = new ArrayList<>();
            if (petsArray.isArray()) {
                for (JsonNode petNode : petsArray) {
                    boolean matches = true;
                    if (request.getType() != null && !request.getType().isEmpty()) {
                        // type is stored under "category" object with "name" field or "tags"? Petstore API is inconsistent.
                        JsonNode categoryNode = petNode.get("category");
                        if (categoryNode == null || !request.getType().equalsIgnoreCase(categoryNode.path("name").asText(""))) {
                            matches = false;
                        }
                    }
                    if (matches && request.getName() != null && !request.getName().isEmpty()) {
                        String petName = petNode.path("name").asText("").toLowerCase();
                        if (!petName.contains(request.getName().toLowerCase())) {
                            matches = false;
                        }
                    }
                    if (matches) {
                        filteredPets.add(petNode);
                    }
                }
            }

            logger.info("Search found {} pets matching criteria", filteredPets.size());
            return filteredPets;

        } catch (Exception e) {
            logger.error("Error during external Petstore API call or processing", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch pets from external API");
        }
    }

    /**
     * POST /prototype/pets/favorites/add
     * Adds a pet to user's favorites list stored internally.
     */
    @PostMapping("/favorites/add")
    public Map<String, Object> addFavorite(@RequestBody AddFavoriteRequest request) {
        logger.info("Adding petId={} to favorites for userId={}", request.getPetId(), request.getUserId());

        if (request.getPetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId must be provided");
        }

        userFavorites.computeIfAbsent(request.getUserId(), k -> ConcurrentHashMap.newKeySet())
                .add(request.getPetId());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Pet added to favorites");
        return response;
    }

    /**
     * GET /prototype/pets/favorites/{userId}
     * Retrieves favorite pets list for a user.
     */
    @GetMapping("/favorites/{userId}")
    public List<JsonNode> getFavorites(@PathVariable String userId) {
        logger.info("Fetching favorites for userId={}", userId);

        Set<Integer> favorites = userFavorites.getOrDefault(userId, Collections.emptySet());
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsonNode> favoritePets = new ArrayList<>();
        for (Integer petId : favorites) {
            try {
                Mono<String> responseMono = webClient.get()
                        .uri("/pet/{petId}", petId)
                        .retrieve()
                        .bodyToMono(String.class);

                String petJson = responseMono.block(); // blocking call for prototype
                JsonNode petNode = objectMapper.readTree(petJson);
                favoritePets.add(petNode);
            } catch (Exception e) {
                logger.error("Failed to fetch pet details for petId={}", petId, e);
                // skip failed pet
            }
        }

        logger.info("Returning {} favorite pets for userId={}", favoritePets.size(), userId);
        return favoritePets;
    }

    /**
     * POST /prototype/pets/details
     * Retrieves detailed information for a specific pet by invoking external Petstore API.
     */
    @PostMapping("/details")
    public JsonNode getPetDetails(@RequestBody PetDetailsRequest request) {
        logger.info("Fetching pet details for petId={}", request.getPetId());

        if (request.getPetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId must be provided");
        }

        try {
            Mono<String> responseMono = webClient.get()
                    .uri("/pet/{petId}", request.getPetId())
                    .retrieve()
                    .bodyToMono(String.class);

            String petJson = responseMono.block(); // blocking call prototype
            JsonNode petNode = objectMapper.readTree(petJson);
            return petNode;
        } catch (Exception e) {
            logger.error("Failed to fetch pet details from external API for petId={}", request.getPetId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch pet details");
        }
    }

    // Basic error handler for ResponseStatusException

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException - status: {}, message: {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

}