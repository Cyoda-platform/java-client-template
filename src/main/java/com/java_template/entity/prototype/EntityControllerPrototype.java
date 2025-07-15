package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<Long, Pet> petStorage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private long petIdSequence = 1L;

    /**
     * POST /pets/fetch
     * Fetch and process pets data from the external Petstore API.
     * Request contains optional filter by status.
     */
    @PostMapping(path = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public FetchResponse fetchPets(@RequestBody(required = false) FetchRequest fetchRequest) {
        String filterStatus = (fetchRequest != null && fetchRequest.getFilter() != null)
                ? fetchRequest.getFilter().getStatus()
                : null;

        logger.info("Received fetchPets request with filter status: {}", filterStatus);

        try {
            // Build external API URL with optional status query param
            String url = "https://petstore.swagger.io/v2/pet/findByStatus";
            if (filterStatus == null || filterStatus.isBlank()) {
                // Default to "available" if no filter provided
                filterStatus = "available";
            }
            String requestUrl = url + "?status=" + filterStatus;

            logger.info("Calling external Petstore API: {}", requestUrl);

            String response = restTemplate.getForObject(requestUrl, String.class);

            JsonNode rootNode = objectMapper.readTree(response);
            if (!rootNode.isArray()) {
                logger.error("Unexpected response format from Petstore API: not an array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external Petstore API");
            }

            int count = 0;
            for (JsonNode petNode : rootNode) {
                Pet pet = parsePetFromJsonNode(petNode);
                if (pet != null) {
                    // Generate new local pet ID
                    long newId = generatePetId();
                    pet.setId(newId);
                    petStorage.put(newId, pet);
                    count++;
                }
            }

            logger.info("Fetched and stored {} pets", count);

            // TODO: Consider making this async in real implementation
            return new FetchResponse("Pets data fetched and processed successfully", count);

        } catch (Exception ex) {
            logger.error("Error fetching pets from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    /**
     * GET /pets
     * Retrieve the list of pets stored or processed by the app.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Pet> getPets() {
        logger.info("Returning list of {} stored pets", petStorage.size());
        return petStorage.values();
    }

    /**
     * POST /pets/match
     * Calculate and return pet matches based on user preferences.
     */
    @PostMapping(path = "/match", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MatchResponse matchPets(@RequestBody MatchRequest matchRequest) {
        logger.info("Matching pets for category: {}, status: {}", matchRequest.getPreferredCategory(), matchRequest.getPreferredStatus());

        List<Pet> matches = new ArrayList<>();
        for (Pet pet : petStorage.values()) {
            boolean categoryMatches = matchRequest.getPreferredCategory() == null
                    || pet.getCategory() == null
                    || pet.getCategory().equalsIgnoreCase(matchRequest.getPreferredCategory());
            boolean statusMatches = matchRequest.getPreferredStatus() == null
                    || pet.getStatus() == null
                    || pet.getStatus().equalsIgnoreCase(matchRequest.getPreferredStatus());

            if (categoryMatches && statusMatches) {
                matches.add(pet);
            }
        }

        logger.info("Found {} matching pets", matches.size());
        return new MatchResponse(matches);
    }

    /**
     * GET /pets/{id}
     * Retrieve detailed information about a single pet by ID.
     */
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable("id") Long id) {
        logger.info("Fetching pet with id {}", id);
        Pet pet = petStorage.get(id);
        if (pet == null) {
            logger.error("Pet with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    // --- Helper methods and DTOs ---

    private synchronized long generatePetId() {
        return petIdSequence++;
    }

    private Pet parsePetFromJsonNode(JsonNode petNode) {
        try {
            Pet pet = new Pet();

            JsonNode idNode = petNode.get("id");
            if (idNode != null && idNode.isIntegralNumber()) {
                // We ignore external id and generate our own local id
            }

            JsonNode nameNode = petNode.get("name");
            if (nameNode != null && nameNode.isTextual()) {
                pet.setName(nameNode.asText());
            }

            JsonNode statusNode = petNode.get("status");
            if (statusNode != null && statusNode.isTextual()) {
                pet.setStatus(statusNode.asText());
            }

            JsonNode categoryNode = petNode.get("category");
            if (categoryNode != null && categoryNode.isObject()) {
                JsonNode categoryNameNode = categoryNode.get("name");
                if (categoryNameNode != null && categoryNameNode.isTextual()) {
                    pet.setCategory(categoryNameNode.asText());
                }
            }

            JsonNode descNode = petNode.get("description"); // Petstore API may not have description, so use placeholder
            if (descNode != null && descNode.isTextual()) {
                pet.setDescription(descNode.asText());
            } else {
                pet.setDescription("No description available."); // TODO: Improve description handling
            }

            return pet;
        } catch (Exception ex) {
            logger.error("Failed to parse pet from JSON node", ex);
            return null;
        }
    }

    // --- DTOs ---

    @Data
    public static class FetchRequest {
        private Filter filter;

        @Data
        public static class Filter {
            private String status;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class FetchResponse {
        private final String message;
        private final int count;
    }

    @Data
    public static class MatchRequest {
        private String preferredCategory;
        private String preferredStatus;
    }

    @Data
    @RequiredArgsConstructor
    public static class MatchResponse {
        private final List<Pet> matches;
    }

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status;
        private String description;
    }
}