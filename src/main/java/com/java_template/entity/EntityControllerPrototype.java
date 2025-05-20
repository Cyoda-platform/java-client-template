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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
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

    // Mock storage for pets cached or added locally
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();

    // Simple job status map for async operations if needed
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    // Petstore API base URL (Swagger Petstore v2)
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    private long petIdSequence = 1000L; // local ID generator for added pets

    @PostConstruct
    public void init() {
        logger.info("Starting Purrfect Pets EntityControllerPrototype");
    }

    // --- Data Models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String type;
        private String status;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetRequest {
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePetRequest {
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private Long id;
        private String message;
    }

    // --- Controller Endpoints ---

    /**
     * POST /pets/search
     * Search pets by type, status, or name.
     * This triggers external Petstore API call.
     */
    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody SearchRequest request) {
        logger.info("Received search request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());

        try {
            // Build query param string for Petstore API (status and tags supported)
            // Petstore API accepts status and tags; we map "type" to "tags" for demo purpose
            StringBuilder uriBuilder = new StringBuilder(PETSTORE_BASE_URL + "/pet/findByStatus?");

            // Petstore API expects comma separated status values
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                uriBuilder.append("status=").append(request.getStatus());
            } else {
                // default to available pets if not specified
                uriBuilder.append("status=available");
            }

            URI uri = new URI(uriBuilder.toString());

            String jsonResponse = restTemplate.getForObject(uri, String.class);
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            List<Pet> results = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    Pet pet = mapJsonNodeToPet(node);
                    if (filterPet(pet, request)) {
                        results.add(pet);
                    }
                }
            } else {
                logger.warn("Expected JSON array from Petstore API but got: {}", rootNode);
            }

            // Cache results locally (optional)
            results.forEach(p -> petStore.putIfAbsent(p.getId(), p));

            return ResponseEntity.ok(results);

        } catch (Exception ex) {
            logger.error("Error during searchPets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    /**
     * POST /pets
     * Add a new pet to local store and external Petstore API.
     * Note: Petstore API POST /pet expects full object with id.
     * We generate a local id to simulate.
     */
    @PostMapping
    public ResponseEntity<MessageResponse> addPet(@RequestBody AddPetRequest request) {
        logger.info("Adding new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());

        try {
            long newId = generatePetId();

            Pet pet = new Pet(newId, request.getName(), request.getType(), request.getStatus(), 
                    request.getPhotoUrls() != null ? request.getPhotoUrls() : Collections.emptyList());

            // Prepare JSON payload for Petstore API
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", pet.getId());
            payload.put("name", pet.getName());
            payload.put("photoUrls", pet.getPhotoUrls());
            // Petstore API expects "category" object with "name" for type
            Map<String, String> category = new HashMap<>();
            category.put("name", pet.getType() != null ? pet.getType() : "");
            payload.put("category", category);
            payload.put("status", pet.getStatus());

            // Fire-and-forget external call to Petstore API to add pet
            CompletableFuture.runAsync(() -> {
                try {
                    restTemplate.postForObject(PETSTORE_BASE_URL + "/pet", payload, String.class);
                    logger.info("Successfully added pet to external Petstore API: id={}", pet.getId());
                } catch (Exception e) {
                    logger.error("Failed to add pet to external Petstore API: id={}", pet.getId(), e);
                }
            }); // TODO: handle failure/retries for production

            // Add to local cache immediately
            petStore.put(pet.getId(), pet);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new MessageResponse(pet.getId(), "Pet added successfully"));

        } catch (Exception ex) {
            logger.error("Error during addPet", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add pet");
        }
    }

    /**
     * POST /pets/{id}/update
     * Update pet details locally and in external Petstore API.
     */
    @PostMapping("/{id}/update")
    public ResponseEntity<MessageResponse> updatePet(@PathVariable Long id, @RequestBody UpdatePetRequest request) {
        logger.info("Updating pet id={}", id);

        Pet existingPet = petStore.get(id);
        if (existingPet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found: " + id);
        }

        // Update local pet fields if provided
        if (request.getName() != null) existingPet.setName(request.getName());
        if (request.getType() != null) existingPet.setType(request.getType());
        if (request.getStatus() != null) existingPet.setStatus(request.getStatus());
        if (request.getPhotoUrls() != null) existingPet.setPhotoUrls(request.getPhotoUrls());

        // Prepare JSON payload for Petstore API update
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", existingPet.getId());
        payload.put("name", existingPet.getName());
        payload.put("photoUrls", existingPet.getPhotoUrls());
        Map<String, String> category = new HashMap<>();
        category.put("name", existingPet.getType() != null ? existingPet.getType() : "");
        payload.put("category", category);
        payload.put("status", existingPet.getStatus());

        // Fire-and-forget external update
        CompletableFuture.runAsync(() -> {
            try {
                restTemplate.put(PETSTORE_BASE_URL + "/pet", payload);
                logger.info("Successfully updated pet in external Petstore API: id={}", id);
            } catch (Exception e) {
                logger.error("Failed to update pet in external Petstore API: id={}", id, e);
            }
        }); // TODO: handle failure/retries for production

        petStore.put(id, existingPet);

        return ResponseEntity.ok(new MessageResponse(id, "Pet updated successfully"));
    }

    /**
     * POST /pets/{id}/delete
     * Delete pet locally and in external Petstore API.
     */
    @PostMapping("/{id}/delete")
    public ResponseEntity<MessageResponse> deletePet(@PathVariable Long id) {
        logger.info("Deleting pet id={}", id);

        Pet removed = petStore.remove(id);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found: " + id);
        }

        // Fire-and-forget external delete
        CompletableFuture.runAsync(() -> {
            try {
                restTemplate.delete(PETSTORE_BASE_URL + "/pet/" + id);
                logger.info("Successfully deleted pet in external Petstore API: id={}", id);
            } catch (Exception e) {
                logger.error("Failed to delete pet in external Petstore API: id={}", id, e);
            }
        }); // TODO: handle failure/retries for production

        return ResponseEntity.ok(new MessageResponse(id, "Pet deleted successfully"));
    }

    /**
     * GET /pets/{id}
     * Retrieve pet details from local cache.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long id) {
        logger.info("Retrieving pet id={}", id);

        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found: " + id);
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * GET /pets
     * Retrieve all pets from local cache.
     */
    @GetMapping
    public ResponseEntity<List<Pet>> listAllPets() {
        logger.info("Listing all pets");
        return ResponseEntity.ok(new ArrayList<>(petStore.values()));
    }

    // --- Helper Methods ---

    private long generatePetId() {
        return petIdSequence++;
    }

    /**
     * Map external Petstore API JsonNode to Pet model.
     */
    private Pet mapJsonNodeToPet(JsonNode node) {
        try {
            Long id = node.hasNonNull("id") ? node.get("id").asLong() : null;
            String name = node.hasNonNull("name") ? node.get("name").asText() : "";
            String status = node.hasNonNull("status") ? node.get("status").asText() : "";

            // category.name used as type
            String type = "";
            if (node.hasNonNull("category") && node.get("category").hasNonNull("name")) {
                type = node.get("category").get("name").asText();
            }

            List<String> photoUrls = new ArrayList<>();
            if (node.hasNonNull("photoUrls") && node.get("photoUrls").isArray()) {
                for (JsonNode urlNode : node.get("photoUrls")) {
                    photoUrls.add(urlNode.asText());
                }
            }

            return new Pet(id, name, type, status, photoUrls);
        } catch (Exception e) {
            logger.error("Failed to map JsonNode to Pet", e);
            return null;
        }
    }

    /**
     * Filter pet by SearchRequest criteria.
     */
    private boolean filterPet(Pet pet, SearchRequest req) {
        if (pet == null) return false;
        if (req.getType() != null && !req.getType().isBlank()) {
            if (pet.getType() == null || !pet.getType().equalsIgnoreCase(req.getType())) {
                return false;
            }
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if (pet.getStatus() == null || !pet.getStatus().equalsIgnoreCase(req.getStatus())) {
                return false;
            }
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            if (pet.getName() == null || !pet.getName().toLowerCase().contains(req.getName().toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    // --- Minimal error handling for invalid JSON or bad requests ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = Map.of(
                "error", ex.getReason(),
                "status", String.valueOf(ex.getStatusCode().value())
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        Map<String, String> error = Map.of(
                "error", "Internal server error",
                "details", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```
