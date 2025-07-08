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
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory pet store mock: id -> Pet
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private long petIdSequence = 1;

    // --- Models ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String details; // optional
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SyncRequest {
        private String action;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StatusUpdateRequest {
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
        private String name;
        private String type;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GenericResponse {
        private String message;
        private Long id;
        private String status;
        private Integer syncedCount;
    }

    // --- Endpoints ---

    /**
     * POST /pets/sync
     * Sync pets data from external Petstore API.
     * Body: {"action":"sync"}
     * Response: status, syncedCount
     */
    @PostMapping("/sync")
    public ResponseEntity<GenericResponse> syncPets(@RequestBody SyncRequest request) {
        logger.info("Received sync request with action={}", request.getAction());

        if (!"sync".equalsIgnoreCase(request.getAction())) {
            logger.error("Invalid action for sync: {}", request.getAction());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action. Use 'sync'.");
        }

        // Fire-and-forget: sync in background
        CompletableFuture.runAsync(() -> {
            try {
                performSyncFromPetstore();
            } catch (Exception e) {
                logger.error("Error during petstore sync: ", e);
                // In a real app, persist error state or notify system
            }
        });

        return ResponseEntity.ok(new GenericResponse("sync started", null, "processing", null));
    }

    /**
     * GET /pets
     * Return all locally stored pets.
     */
    @GetMapping
    public ResponseEntity<Object> getAllPets() {
        logger.info("Fetching all pets, count={}", petStore.size());
        return ResponseEntity.ok(petStore.values());
    }

    /**
     * POST /pets
     * Add a new pet to local store.
     */
    @PostMapping
    public ResponseEntity<GenericResponse> addPet(@RequestBody AddPetRequest request) {
        logger.info("Adding new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());

        if (!StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getType()) || !StringUtils.hasText(request.getStatus())) {
            logger.error("Missing required fields in addPet request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields: name, type, status");
        }

        long newId = getNextPetId();
        Pet newPet = new Pet(newId, request.getName(), request.getType(), request.getStatus(), null);
        petStore.put(newId, newPet);

        return ResponseEntity.status(HttpStatus.CREATED).body(new GenericResponse("Pet added successfully", newId, null, null));
    }

    /**
     * GET /pets/{id}
     * Retrieve pet details by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") Long id) {
        logger.info("Fetching pet by id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet not found with id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * POST /pets/{id}/update-status
     * Update pet adoption status by ID.
     */
    @PostMapping("/{id}/update-status")
    public ResponseEntity<GenericResponse> updatePetStatus(@PathVariable("id") Long id,
                                                           @RequestBody StatusUpdateRequest request) {
        logger.info("Updating status for pet id={} to {}", id, request.getStatus());
        if (!StringUtils.hasText(request.getStatus())) {
            logger.error("Missing status in update request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet not found for status update with id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        pet.setStatus(request.getStatus());
        petStore.put(id, pet);

        return ResponseEntity.ok(new GenericResponse("Status updated successfully", id, null, null));
    }

    // --- Helpers ---

    private synchronized long getNextPetId() {
        return petIdSequence++;
    }

    /**
     * Perform sync from external Petstore API.
     * This method fetches data from the official Petstore Swagger example API:
     * https://petstore.swagger.io/v2/pet/findByStatus?status=available
     * 
     * It replaces local petStore content with fetched pets.
     */
    private void performSyncFromPetstore() {
        logger.info("Starting petstore sync at {}", Instant.now());

        try {
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            String jsonResponse = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(jsonResponse);

            if (!root.isArray()) {
                logger.error("Unexpected response from Petstore API, expected array");
                return;
            }

            // Clear current store and reset id sequence
            petStore.clear();
            petIdSequence = 1;

            int count = 0;
            for (JsonNode node : root) {
                Long externalId = node.path("id").asLong(-1);
                String name = node.path("name").asText(null);
                String status = node.path("status").asText("Available");
                String type = "Unknown";

                // Try to get category.name as type if available
                JsonNode categoryNode = node.path("category");
                if (categoryNode != null && categoryNode.has("name")) {
                    type = categoryNode.get("name").asText("Unknown");
                }

                if (externalId == -1 || name == null) {
                    logger.warn("Skipping invalid pet entry from external API");
                    continue;
                }

                long newId = getNextPetId();

                Pet pet = new Pet(newId, name, type, status, null);
                petStore.put(newId, pet);
                count++;
            }

            logger.info("Petstore sync completed successfully, {} pets imported", count);

        } catch (Exception e) {
            logger.error("Exception during petstore sync", e);
            // TODO: Handle retries or notify system in real implementation
        }
    }

    // --- Basic error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "message", "Internal server error"
                ));
    }
}
```