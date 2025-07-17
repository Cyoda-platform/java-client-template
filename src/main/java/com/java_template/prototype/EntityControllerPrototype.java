```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory store for pets (id -> Pet)
    private final Map<String, Pet> petStore = new ConcurrentHashMap<>();

    // External Petstore API base URL (real endpoint)
    private static final String PETSTORE_API_BASE = "https://petstore3.swagger.io/api/v3";

    // ---- POST /pets : add or update pet ----
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddUpdatePetResponse> addOrUpdatePet(@RequestBody Pet pet) {
        log.info("Received add/update request for pet with id: {}", pet.getId());

        // If pet id is null or blank, generate new UUID
        if (pet.getId() == null || pet.getId().isBlank()) {
            pet.setId(UUID.randomUUID().toString());
            log.info("Generated new pet id: {}", pet.getId());
        }

        // Fire-and-forget enrichment from external Petstore API (simulate fetch by name)
        CompletableFuture.runAsync(() -> enrichPetFromExternalApi(pet));

        // Save/update locally
        petStore.put(pet.getId(), pet);

        return ResponseEntity.ok(new AddUpdatePetResponse(true, pet));
    }

    private void enrichPetFromExternalApi(Pet pet) {
        try {
            // TODO: This is placeholder logic that tries to find pet by name from external API and update local data
            // Petstore official openapi does not provide direct search by name endpoint, so we mock this logic

            log.info("Enriching pet {} by invoking external Petstore API", pet.getName());
            // Example: GET /pet/findByStatus?status=available (to simulate fetching some data)
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=available";

            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    if (node.hasNonNull("name") && pet.getName().equalsIgnoreCase(node.get("name").asText())) {
                        // Update category, tags, photoUrls from external data
                        if (node.has("category") && node.get("category").has("name")) {
                            pet.setCategory(node.get("category").get("name").asText());
                        }
                        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
                            List<String> photos = new ArrayList<>();
                            node.get("photoUrls").forEach(urlNode -> photos.add(urlNode.asText()));
                            pet.setPhotoUrls(photos);
                        }
                        if (node.has("tags") && node.get("tags").isArray()) {
                            List<String> tags = new ArrayList<>();
                            node.get("tags").forEach(tagNode -> {
                                if (tagNode.has("name")) tags.add(tagNode.get("name").asText());
                            });
                            pet.setTags(tags);
                        }
                        if (node.has("status")) {
                            pet.setStatus(node.get("status").asText());
                        }
                        log.info("Pet {} enriched with external API data", pet.getId());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to enrich pet {} from external API: {}", pet.getId(), e.getMessage());
            // Swallow exception as enrichment is fire-and-forget
        }
    }

    // ---- GET /pets/{id} : retrieve pet details ----
    @GetMapping(path = "/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) {
        log.info("Retrieving pet by id: {}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // ---- POST /pets/search : search pets by criteria ----
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pet>> searchPets(@RequestBody PetSearchRequest searchRequest) {
        log.info("Searching pets with criteria: name='{}', status='{}', category='{}'",
                searchRequest.getName(), searchRequest.getStatus(), searchRequest.getCategory());

        // Call external Petstore API to get pets by status if status filter is set
        List<Pet> results = new ArrayList<>();

        try {
            if (searchRequest.getStatus() != null && !searchRequest.getStatus().isBlank()) {
                // Use external API findByStatus endpoint
                String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + searchRequest.getStatus();
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        Pet pet = parsePetFromJsonNode(node);
                        if (pet != null && matchesSearchCriteria(pet, searchRequest)) {
                            results.add(pet);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching pets from external API: {}", e.getMessage());
            // Continue with local search even if external fails
        }

        // Also search local store
        petStore.values().stream()
                .filter(pet -> matchesSearchCriteria(pet, searchRequest))
                .forEach(results::add);

        return ResponseEntity.ok(results);
    }

    private boolean matchesSearchCriteria(Pet pet, PetSearchRequest req) {
        if (req.getName() != null && !req.getName().isBlank()) {
            if (!pet.getName().equalsIgnoreCase(req.getName())) return false;
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            if (!req.getCategory().equalsIgnoreCase(pet.getCategory())) return false;
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if (!req.getStatus().equalsIgnoreCase(pet.getStatus())) return false;
        }
        return true;
    }

    private Pet parsePetFromJsonNode(JsonNode node) {
        try {
            Pet pet = new Pet();
            if (node.hasNonNull("id")) pet.setId(node.get("id").asText());
            if (node.hasNonNull("name")) pet.setName(node.get("name").asText());
            if (node.has("category") && node.get("category").hasNonNull("name"))
                pet.setCategory(node.get("category").get("name").asText());
            if (node.hasNonNull("status")) pet.setStatus(node.get("status").asText());

            if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
                List<String> photos = new ArrayList<>();
                node.get("photoUrls").forEach(urlNode -> photos.add(urlNode.asText()));
                pet.setPhotoUrls(photos);
            }

            if (node.has("tags") && node.get("tags").isArray()) {
                List<String> tags = new ArrayList<>();
                node.get("tags").forEach(tagNode -> {
                    if (tagNode.has("name")) tags.add(tagNode.get("name").asText());
                });
                pet.setTags(tags);
            }
            return pet;
        } catch (Exception e) {
            log.error("Failed to parse pet from JSON node: {}", e.getMessage());
            return null;
        }
    }

    // ---- POST /pets/delete : delete pet by ID ----
    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeletePetResponse> deletePet(@RequestBody DeletePetRequest request) {
        log.info("Deleting pet with id: {}", request.getId());

        Pet removed = petStore.remove(request.getId());
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        return ResponseEntity.ok(new DeletePetResponse(true, "Pet deleted successfully"));
    }

    // ---- Exception handler for validation and other errors ----
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // --- DTOs and model classes ---

    @Data
    public static class Pet {
        private String id;
        @NotBlank
        private String name;
        private String category;
        private String status;
        private List<String> tags = new ArrayList<>();
        private List<String> photoUrls = new ArrayList<>();
    }

    @Data
    public static class AddUpdatePetResponse {
        private final boolean success;
        private final Pet pet;
    }

    @Data
    public static class PetSearchRequest {
        private String name;
        private String status;
        private String category;
    }

    @Data
    public static class DeletePetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class DeletePetResponse {
        private final boolean success;
        private final String message;
    }
}
```