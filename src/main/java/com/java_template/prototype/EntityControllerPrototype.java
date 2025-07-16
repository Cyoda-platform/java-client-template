```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
@RequiredArgsConstructor
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Simulating auto-increment ID generation
    private long idSequence = 1;

    // External Petstore API base (using Swagger Petstore public API)
    private static final String EXTERNAL_PETSTORE_API = "https://petstore3.swagger.io/api/v3/pet";

    // ========== DTOs ==========

    @Data
    public static class Pet {
        private Long id;
        @NotBlank
        private String name;
        private String category;
        private List<String> tags = new ArrayList<>();
        private String status; // available | pending | sold
    }

    @Data
    public static class PetSearchRequest {
        private String category;
        private String status;
        private List<String> tags = new ArrayList<>();
    }

    @Data
    public static class PetSearchResponse {
        private List<Pet> results = new ArrayList<>();
    }

    @Data
    public static class FunFactResponse {
        private String fact;
    }

    @Data
    public static class AddOrUpdatePetResponse {
        private boolean success;
        private Pet pet;
    }

    // ========== Endpoints ==========

    /**
     * Add new or update existing pet.
     * This POST endpoint enriches/validates pet data by calling the external Petstore API.
     */
    @PostMapping
    public ResponseEntity<AddOrUpdatePetResponse> addOrUpdatePet(@Valid @RequestBody Pet petRequest) {
        logger.info("Received add/update pet request: {}", petRequest);

        try {
            // If petRequest.id is null -> add new, else update existing
            Pet petToSave;
            if (petRequest.getId() == null) {
                petToSave = new Pet();
                petToSave.setId(generateId());
            } else {
                petToSave = petStore.get(petRequest.getId());
                if (petToSave == null) {
                    logger.info("Pet with id {} not found, will create new.", petRequest.getId());
                    petToSave = new Pet();
                    petToSave.setId(petRequest.getId());
                }
            }

            // Copy fields
            petToSave.setName(petRequest.getName());
            petToSave.setCategory(petRequest.getCategory());
            petToSave.setTags(petRequest.getTags() != null ? petRequest.getTags() : new ArrayList<>());
            petToSave.setStatus(petRequest.getStatus());

            // Enrich/validate using external Petstore API (mock partial enrich by fetching external pet)
            // TODO: Improve enrichment/validation logic with real external API calls or business logic

            // Example: try to GET pet by ID at external API to validate if ID exists (only if ID provided)
            if (petRequest.getId() != null) {
                try {
                    String url = EXTERNAL_PETSTORE_API + "/" + petRequest.getId();
                    String externalResponse = restTemplate.getForObject(url, String.class);
                    JsonNode jsonNode = objectMapper.readTree(externalResponse);
                    logger.info("External API validation success for pet id {}: {}", petRequest.getId(), jsonNode);
                    // Could add more validation or enrichment from jsonNode here
                } catch (Exception ex) {
                    logger.warn("Could not validate pet id {} on external API: {}", petRequest.getId(), ex.getMessage());
                }
            }

            petStore.put(petToSave.getId(), petToSave);

            AddOrUpdatePetResponse response = new AddOrUpdatePetResponse();
            response.setSuccess(true);
            response.setPet(petToSave);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error in addOrUpdatePet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error while adding/updating pet", e);
        }
    }

    /**
     * Get pet info by ID (only from internal store).
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long id) {
        logger.info("Received get pet by id: {}", id);

        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * Search pets using criteria.
     * POST endpoint calls external API to enrich results.
     */
    @PostMapping("/search")
    public ResponseEntity<PetSearchResponse> searchPets(@RequestBody PetSearchRequest searchRequest) {
        logger.info("Received search pets request: {}", searchRequest);

        try {
            List<Pet> filteredPets = new ArrayList<>();

            // Basic filter on local store
            for (Pet pet : petStore.values()) {
                if (matchesSearch(pet, searchRequest)) {
                    filteredPets.add(pet);
                }
            }

            // Enrich results with external Petstore API data (TODO: improve with real integration)
            // For demo, fetch all pets from external API (mocked with GET /pet/findByStatus?status=available)
            try {
                String url = "https://petstore3.swagger.io/api/v3/pet/findByStatus?status=available";
                String externalResponse = restTemplate.getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(externalResponse);
                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        Pet extPet = jsonNodeToPet(node);
                        if (matchesSearch(extPet, searchRequest)) {
                            filteredPets.add(extPet);
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("External API pet search failed: {}", ex.getMessage());
            }

            PetSearchResponse response = new PetSearchResponse();
            response.setResults(filteredPets);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in searchPets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error while searching pets", e);
        }
    }

    /**
     * Fun feature: return random pet fact.
     * POST endpoint - simulates external API call.
     */
    @PostMapping("/fun/fact")
    public ResponseEntity<FunFactResponse> randomPetFact() {
        logger.info("Received request for random pet fact");

        try {
            // TODO: Replace with real external API or database of pet facts
            List<String> facts = List.of(
                    "Cats sleep for 70% of their lives.",
                    "Dogs have three eyelids.",
                    "Goldfish can distinguish music genres.",
                    "Rabbits can't vomit."
            );

            Random rand = new Random();
            String fact = facts.get(rand.nextInt(facts.size()));

            FunFactResponse response = new FunFactResponse();
            response.setFact(fact);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in randomPetFact", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error while retrieving pet fact", e);
        }
    }

    // ========== Helpers ==========

    private synchronized long generateId() {
        return idSequence++;
    }

    private boolean matchesSearch(Pet pet, PetSearchRequest req) {
        if (req.getCategory() != null && (pet.getCategory() == null ||
                !pet.getCategory().equalsIgnoreCase(req.getCategory()))) {
            return false;
        }
        if (req.getStatus() != null && (pet.getStatus() == null ||
                !pet.getStatus().equalsIgnoreCase(req.getStatus()))) {
            return false;
        }
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            // Check if pet tags contain all requested tags (case insensitive)
            for (String tag : req.getTags()) {
                boolean found = false;
                for (String petTag : pet.getTags()) {
                    if (petTag.equalsIgnoreCase(tag)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
        }
        return true;
    }

    private Pet jsonNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        pet.setId(node.has("id") && !node.get("id").isNull() ? node.get("id").asLong() : null);
        pet.setName(node.has("name") && !node.get("name").isNull() ? node.get("name").asText() : null);

        if (node.has("category") && node.get("category").has("name")) {
            pet.setCategory(node.get("category").get("name").asText());
        }

        List<String> tags = new ArrayList<>();
        if (node.has("tags") && node.get("tags").isArray()) {
            for (JsonNode tagNode : node.get("tags")) {
                if (tagNode.has("name")) {
                    tags.add(tagNode.get("name").asText());
                }
            }
        }
        pet.setTags(tags);

        pet.setStatus(node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null);

        return pet;
    }

    // ========== Minimal error handling ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Unexpected error occurred");
        logger.error("Unhandled exception: ", ex);
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```