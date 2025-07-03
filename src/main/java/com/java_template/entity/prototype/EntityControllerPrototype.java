```java
package com.java_template.entity.prototype;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/pets")
public class EntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory pet store: id -> Pet
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private long petIdSequence = 1000; // simple id sequence for local pets

    // --- DTOs and Models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String status;
        private List<String> tags;
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncFilter {
        private String status; // available, pending, sold
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncRequest {
        private SyncFilter filter;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetsResponse {
        private List<Pet> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetRequest {
        private String name;
        private String status;
        private List<String> tags;
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetResponse {
        private Long id;
        private String name;
        private String status;
        private List<String> tags;
        private String category;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusResponse {
        private Long id;
        private String status;
        private String message;
    }

    // --- Endpoints ---

    /**
     * POST /prototype/pets/sync
     * Fetches pets from external Petstore API with optional filtering,
     * stores them locally, and returns the synced pets list.
     */
    @PostMapping("/sync")
    public ResponseEntity<PetsResponse> syncPetsFromExternal(@RequestBody(required = false) SyncRequest request) {
        logger.info("Received sync request with filter: {}", request);

        String externalApiUrl = "https://petstore.swagger.io/v2/pet/findByStatus";

        // Determine status filter parameter
        String statusParam = "available";
        if (request != null && request.getFilter() != null && request.getFilter().getStatus() != null) {
            statusParam = request.getFilter().getStatus();
        }

        String urlWithParams = externalApiUrl + "?status=" + statusParam;

        try {
            // Fetch pets JSON array from external API
            String rawResponse = restTemplate.getForObject(urlWithParams, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);

            if (!rootNode.isArray()) {
                logger.error("Unexpected response format from external Petstore API: expected array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid external API response format");
            }

            List<Pet> syncedPets = new ArrayList<>();

            for (JsonNode petNode : rootNode) {
                // Extract fields with fallbacks
                Long id = petNode.hasNonNull("id") ? petNode.get("id").asLong() : null;
                String name = petNode.hasNonNull("name") ? petNode.get("name").asText() : "Unnamed";
                String status = petNode.hasNonNull("status") ? petNode.get("status").asText() : "unknown";

                // Extract tags names if present
                List<String> tagsList = new ArrayList<>();
                if (petNode.has("tags") && petNode.get("tags").isArray()) {
                    for (JsonNode tagNode : petNode.get("tags")) {
                        if (tagNode.hasNonNull("name")) {
                            tagsList.add(tagNode.get("name").asText());
                        }
                    }
                }

                // Extract category name if present
                String category = null;
                if (petNode.has("category") && petNode.get("category").hasNonNull("name")) {
                    category = petNode.get("category").get("name").asText();
                }

                // Filter by tags if provided in request
                if (request != null && request.getFilter() != null && request.getFilter().getTags() != null
                        && !request.getFilter().getTags().isEmpty()) {
                    boolean matchesTag = false;
                    for (String tagFilter : request.getFilter().getTags()) {
                        if (tagsList.contains(tagFilter)) {
                            matchesTag = true;
                            break;
                        }
                    }
                    if (!matchesTag) {
                        continue; // skip pet if no matching tags
                    }
                }

                Pet pet = new Pet(id, name, status, tagsList, category);
                if (pet.getId() != null) {
                    petStore.put(pet.getId(), pet);
                } else {
                    // Assign a local id if external id missing
                    pet.setId(generateNextPetId());
                    petStore.put(pet.getId(), pet);
                }
                syncedPets.add(pet);
            }

            logger.info("Synced {} pets from external Petstore API", syncedPets.size());

            return ResponseEntity.ok(new PetsResponse(syncedPets));

        } catch (Exception e) {
            logger.error("Error syncing pets from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to sync from external Petstore API");
        }
    }

    /**
     * GET /prototype/pets
     * Returns all locally stored pets.
     */
    @GetMapping
    public ResponseEntity<PetsResponse> getAllPets() {
        logger.info("Retrieving all local pets, count={}", petStore.size());
        return ResponseEntity.ok(new PetsResponse(new ArrayList<>(petStore.values())));
    }

    /**
     * POST /prototype/pets
     * Adds a new pet locally.
     */
    @PostMapping
    public ResponseEntity<AddPetResponse> addNewPet(@RequestBody AddPetRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet name is required");
        }
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet status is required");
        }
        Long newId = generateNextPetId();
        Pet pet = new Pet(newId, request.getName(), request.getStatus(),
                request.getTags() != null ? request.getTags() : Collections.emptyList(),
                request.getCategory());

        petStore.put(newId, pet);

        logger.info("Added new pet with id {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AddPetResponse(pet.getId(), pet.getName(), pet.getStatus(), pet.getTags(), pet.getCategory(),
                        "Pet added successfully"));
    }

    /**
     * POST /prototype/pets/{id}/status
     * Updates the status of an existing pet.
     */
    @PostMapping("/{id}/status")
    public ResponseEntity<UpdateStatusResponse> updatePetStatus(@PathVariable("id") Long id,
                                                                @RequestBody UpdateStatusRequest request) {
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet with id " + id + " not found");
        }

        pet.setStatus(request.getStatus());
        petStore.put(id, pet);

        logger.info("Updated pet id {} status to {}", id, request.getStatus());
        return ResponseEntity.ok(new UpdateStatusResponse(id, request.getStatus(), "Pet status updated"));
    }

    // --- Helpers ---

    private synchronized Long generateNextPetId() {
        return ++petIdSequence;
    }

    // --- Basic Error Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason() != null ? ex.getReason() : "Unexpected error");
        logger.error("Handled ResponseStatusException: {}", error);
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

}
```