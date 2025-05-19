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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for pets added internally (id -> Pet)
    private final Map<Integer, Pet> internalPetStore = new ConcurrentHashMap<>();
    private int petIdSequence = 1000;

    // External Petstore API base URL (public Swagger Petstore)
    private static final String EXTERNAL_API_BASE = "https://petstore.swagger.io/v2";

    // === API Models ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Integer id;
        private String name;
        private String category;
        private String status;
        private List<String> photoUrls = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
    }

    @Data
    public static class SearchRequest {
        private String status;
        private String category;
        private String name;
    }

    @Data
    public static class AddPetRequest {
        private String name;
        private String category;
        private String status;
        private List<String> photoUrls = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    public static class AddPetResponse {
        private String message;
        private Integer id;
    }

    @Data
    public static class SearchResponse {
        private List<Pet> pets = new ArrayList<>();
    }

    // === Controller Methods ===

    /**
     * POST /pets/search
     * Searches pets via external Petstore API applying filters.
     * Business logic and external API call are in POST endpoint per requirements.
     */
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody SearchRequest request) {
        logger.info("Received search request: status={}, category={}, name={}",
                request.getStatus(), request.getCategory(), request.getName());

        try {
            // Build query params for external API
            StringBuilder uriBuilder = new StringBuilder(EXTERNAL_API_BASE + "/pet/findByStatus?status=");
            // Default to "available" if null or empty
            String statusParam = (request.getStatus() == null || request.getStatus().isEmpty()) ? "available" : request.getStatus();
            uriBuilder.append(statusParam);

            URI uri = new URI(uriBuilder.toString());
            String rawResponse = restTemplate.getForObject(uri, String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            List<Pet> filteredPets = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode petNode : root) {
                    String petCategory = petNode.path("category").path("name").asText("");
                    String petName = petNode.path("name").asText("");
                    String petStatus = statusParam; // we queried by status

                    // Apply category and name filters if provided
                    boolean matchesCategory = (request.getCategory() == null || request.getCategory().isEmpty()) ||
                            petCategory.equalsIgnoreCase(request.getCategory());
                    boolean matchesName = (request.getName() == null || request.getName().isEmpty()) ||
                            petName.toLowerCase().contains(request.getName().toLowerCase());

                    if (matchesCategory && matchesName) {
                        Pet pet = new Pet();
                        pet.setId(petNode.path("id").asInt());
                        pet.setName(petName);
                        pet.setStatus(petStatus);
                        pet.setCategory(petCategory);
                        // photoUrls might be missing or empty
                        List<String> photos = new ArrayList<>();
                        if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                            petNode.get("photoUrls").forEach(urlNode -> photos.add(urlNode.asText()));
                        }
                        pet.setPhotoUrls(photos);

                        filteredPets.add(pet);
                    }
                }
            }

            SearchResponse response = new SearchResponse();
            response.setPets(filteredPets);

            logger.info("Returning {} pets from search", filteredPets.size());
            return response;

        } catch (Exception ex) {
            logger.error("Failed to search pets from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external source");
        }
    }

    /**
     * GET /pets/{id}
     * Retrieve pet details from internal store or fallback to external API.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable("id") Integer id) {
        logger.info("Fetching pet by id: {}", id);

        Pet pet = internalPetStore.get(id);
        if (pet != null) {
            logger.info("Found pet in internal store with id {}", id);
            return pet;
        }

        // Fallback: fetch from external API
        try {
            URI uri = new URI(EXTERNAL_API_BASE + "/pet/" + id);
            String rawResponse = restTemplate.getForObject(uri, String.class);
            JsonNode petNode = objectMapper.readTree(rawResponse);

            if (petNode.has("id")) {
                Pet externalPet = new Pet();
                externalPet.setId(petNode.path("id").asInt());
                externalPet.setName(petNode.path("name").asText(""));
                externalPet.setStatus(petNode.path("status").asText(""));
                externalPet.setCategory(petNode.path("category").path("name").asText(""));
                List<String> photos = new ArrayList<>();
                if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                    petNode.get("photoUrls").forEach(urlNode -> photos.add(urlNode.asText()));
                }
                externalPet.setPhotoUrls(photos);

                List<String> tags = new ArrayList<>();
                if (petNode.has("tags") && petNode.get("tags").isArray()) {
                    petNode.get("tags").forEach(tagNode -> tags.add(tagNode.path("name").asText("")));
                }
                externalPet.setTags(tags);

                return externalPet;
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found externally");
            }

        } catch (ResponseStatusException re) {
            logger.error("Pet not found externally: {}", id);
            throw re;
        } catch (Exception ex) {
            logger.error("Error fetching pet externally id={}", id, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet from external source");
        }
    }

    /**
     * POST /pets/add
     * Add a new pet to internal store.
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddPetResponse addPet(@RequestBody AddPetRequest request) {
        logger.info("Adding new pet: name={}, category={}, status={}",
                request.getName(), request.getCategory(), request.getStatus());

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet name is required");
        }
        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet status is required");
        }

        int newId = generatePetId();
        Pet pet = new Pet(newId,
                request.getName(),
                request.getCategory(),
                request.getStatus(),
                Optional.ofNullable(request.getPhotoUrls()).orElse(Collections.emptyList()),
                Optional.ofNullable(request.getTags()).orElse(Collections.emptyList()));

        internalPetStore.put(newId, pet);

        // TODO: Fire-and-forget any background processing or workflow triggers for new pet
        CompletableFuture.runAsync(() -> {
            logger.info("Background processing for new pet id={}", newId);
            // Placeholder for event-driven processing/workflow
        });

        return new AddPetResponse("Pet added successfully", newId);
    }

    /**
     * GET /pets/list
     * List all internally stored pets, no external calls.
     */
    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> listPets() {
        logger.info("Listing all internally stored pets, count={}", internalPetStore.size());
        return new ArrayList<>(internalPetStore.values());
    }

    // === Helpers ===

    private synchronized int generatePetId() {
        return petIdSequence++;
    }

    // === Minimal error handler example ===

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        return error;
    }

}
```