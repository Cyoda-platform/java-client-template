package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final String EXTERNAL_API_BASE = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory cache for pets by ID
    private final Map<Long, Pet> petCache = new ConcurrentHashMap<>();

    // --- DTOs ---

    @Data
    public static class SearchRequest {
        private String type;
        private String status;
        private String name;
    }

    @Data
    public static class SearchResponse {
        private Pet[] pets;
    }

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String[] tags;
    }

    @Data
    public static class AdoptRequest {
        private Long petId;
        private Long userId;
    }

    @Data
    public static class AdoptResponse {
        private boolean success;
        private String message;
    }

    @Data
    public static class RecommendRequest {
        private Long userId;
        private Preferences preferences;

        @Data
        public static class Preferences {
            private String type;
            private String status;
        }
    }

    @Data
    public static class RecommendResponse {
        private Pet[] recommendedPets;
    }

    // --- Endpoints ---

    /**
     * POST /prototype/pets/search
     * Search pets by criteria by calling external Petstore API and applying simple filters.
     */
    @PostMapping("/search")
    public SearchResponse searchPets(@Valid @RequestBody SearchRequest request) {
        log.info("Received search request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());

        try {
            // External call to get all pets with status=available (example)
            String externalUrl = EXTERNAL_API_BASE + "/pet/findByStatus?status=available";
            JsonNode responseNode = restTemplate.getForObject(externalUrl, JsonNode.class);
            if (responseNode == null || !responseNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external Petstore API");
            }

            // Filter pets according to request criteria and map to Pet[]
            // TODO: improve filtering logic and enrich data
            var filteredPets = responseNode.findValuesAsText("id").stream()
                    .map(idText -> {
                        try {
                            long id = Long.parseLong(idText);
                            JsonNode petNode = responseNode.findValue(idText);
                            // Not reliable to find by idText, so instead map over all
                            // Instead, just filter on fields below:
                            return null; // placeholder
                        } catch (Exception e) {
                            return null;
                        }
                    }).toArray(Pet[]::new);

            // Simple manual filtering instead:
            var petsList = responseNode.findValues(null);
            var petsBuilder = new java.util.ArrayList<Pet>();
            for (JsonNode petNode : responseNode) {
                Pet pet = new Pet();
                pet.setId(petNode.path("id").asLong());
                pet.setName(petNode.path("name").asText(null));
                pet.setStatus(petNode.path("status").asText(null));
                var catNode = petNode.path("category");
                pet.setType(catNode.isMissingNode() ? null : catNode.path("name").asText(null));
                if (petNode.has("tags") && petNode.get("tags").isArray()) {
                    var tagsArr = petNode.get("tags");
                    String[] tags = new String[tagsArr.size()];
                    for (int i = 0; i < tagsArr.size(); i++) {
                        tags[i] = tagsArr.get(i).path("name").asText("");
                    }
                    pet.setTags(tags);
                } else {
                    pet.setTags(new String[0]);
                }

                boolean matches = true;
                if (request.getType() != null && !request.getType().equalsIgnoreCase(pet.getType())) {
                    matches = false;
                }
                if (request.getStatus() != null && !request.getStatus().equalsIgnoreCase(pet.getStatus())) {
                    matches = false;
                }
                if (request.getName() != null && (pet.getName() == null || !pet.getName().toLowerCase().contains(request.getName().toLowerCase()))) {
                    matches = false;
                }
                if (matches) {
                    petsBuilder.add(pet);
                    petCache.put(pet.getId(), pet); // cache pet for GET by ID
                }
            }

            SearchResponse result = new SearchResponse();
            result.setPets(petsBuilder.toArray(new Pet[0]));
            log.info("Search returned {} pets", result.getPets().length);
            return result;
        } catch (Exception e) {
            log.error("Error during pet search", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch data from Petstore API");
        }
    }

    /**
     * GET /prototype/pets/{id}
     * Retrieve cached pet details by ID.
     */
    @GetMapping("/{id}")
    public Pet getPetById(@PathVariable Long id) {
        log.info("Received get pet by ID request: {}", id);
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.warn("Pet ID {} not found in cache", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    /**
     * POST /prototype/pets/adopt
     * Process pet adoption request.
     */
    @PostMapping("/adopt")
    public AdoptResponse adoptPet(@Valid @RequestBody AdoptRequest request) {
        log.info("Adoption request received: petId={}, userId={}", request.getPetId(), request.getUserId());

        Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            log.warn("Adoption failed: Pet ID {} not found", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            log.warn("Adoption failed: Pet ID {} is not available (status={})", request.getPetId(), pet.getStatus());
            AdoptResponse resp = new AdoptResponse();
            resp.setSuccess(false);
            resp.setMessage("Pet is not available for adoption");
            return resp;
        }

        // TODO: Replace this with real external API call to update status & verify availability
        // Fire-and-forget simulation: update status in cache
        pet.setStatus("adopted");
        petCache.put(pet.getId(), pet);

        AdoptResponse resp = new AdoptResponse();
        resp.setSuccess(true);
        resp.setMessage("Adoption successful");
        log.info("Adoption successful for petId={}", request.getPetId());
        return resp;
    }

    /**
     * POST /prototype/pets/recommend
     * Recommend pets based on user preferences.
     */
    @PostMapping("/recommend")
    public RecommendResponse recommendPets(@Valid @RequestBody RecommendRequest request) {
        log.info("Recommend pets request for userId={}, preferences={}", request.getUserId(), request.getPreferences());

        // TODO: Currently uses the same search logic with preferences applied
        SearchRequest searchRequest = new SearchRequest();
        if (request.getPreferences() != null) {
            searchRequest.setType(request.getPreferences().getType());
            searchRequest.setStatus(request.getPreferences().getStatus());
        }

        SearchResponse searchResponse = searchPets(searchRequest);

        RecommendResponse response = new RecommendResponse();
        response.setRecommendedPets(searchResponse.getPets());

        return response;
    }

    // --- Basic Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
    }
}